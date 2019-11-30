package io.mindustry.plugin;

import io.anuke.arc.Core;
import io.anuke.arc.files.FileHandle;
import io.anuke.arc.files.FileHandleStream;
import io.anuke.arc.graphics.PixmapIO;
import io.anuke.arc.maps.TileSet;
import io.anuke.arc.util.Log;
import io.anuke.arc.util.ScreenUtils;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.content.Blocks;
import io.anuke.mindustry.core.GameState;
import io.anuke.mindustry.core.World;
import io.anuke.mindustry.game.Gamemode;
import io.anuke.mindustry.game.Teams;
import io.anuke.mindustry.io.MapIO;
import io.anuke.mindustry.io.SaveIO;
import io.anuke.mindustry.maps.Map;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.Floor;
import io.anuke.mindustry.world.blocks.StaticWall;
import io.anuke.mindustry.world.blocks.storage.CoreBlock;
import io.anuke.mindustry.world.meta.BlockFlag;
import io.mindustry.plugin.discordcommands.DiscordCommands;
import io.mindustry.plugin.discordcommands.MessageCreatedListener;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.event.message.MessageCreateEvent;
import org.json.JSONObject;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.InflaterInputStream;

public class MessageCreatedListeners {
    private final JSONObject data;
    private static final String mapSubmissionSettings = "mapSubmissionSettings";

    public MessageCreatedListeners(JSONObject data){
        this.data = data;
    }
    public void registerListeners(DiscordCommands handler){
        handler.registerOnMessage(new MessageCreatedListener() {
            private final String mapTooSmall = "This map is too small";
            private final String mapUnderBuildableThreshold = "This map has too little buildable land";
            private final String mapHasInvalidName = "This map has an invalid name";
            private final String mapIsInvalid = "This map is invalid";
            private final String mapIsValid = "This map is valid! Please wait for us to review it.";
            private final String mapInvalidForMode = "This map is invalid for the mode %s";
            private final String invalidMapReply = "%s! Please check pinned messages.";
            private final MapReader mapReader = new MapReader();
            private final String[] validModes = new String[] {
                    "survival",
                    "attack",
                    "sandbox",
                    "pvp"
            };
            private JSONObject settings;

            @Override
            public void run(MessageCreateEvent messageCreateEvent) {
                if(data.has(mapSubmissionSettings)){
                    settings = data.getJSONObject(mapSubmissionSettings);
                    long rawID = settings.getLong("channelID");
                    if(rawID == messageCreateEvent.getChannel().getId()){
                        for (MessageAttachment attachment: messageCreateEvent.getMessageAttachments()) {
                            String fileName = attachment.getFileName();
                            if(fileName.endsWith(".msav")){
                                for(String prefix: validModes){
                                    if(fileName.startsWith(prefix + '_')){
                                        attachment.downloadAsByteArray().thenAccept(data -> {
                                            try {
                                                String reply;
                                                if(SaveIO.isSaveValid(new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data))))){
                                                    Map map = MapIO.createMap(new VirtualFileHandle(data), true);
                                                    MapReader.Mtile[][] tiles = mapReader.parseMap(new ByteArrayInputStream(data)).tiles;
                                                    reply = valiadateMap(map, tiles, prefix);
                                                } else {
                                                    reply = mapIsInvalid;
                                                }

                                                if (reply.equals(mapIsValid)) {
                                                    messageCreateEvent.getMessage().addReaction("✅");
                                                } else {
                                                    reply = String.format(invalidMapReply, reply);
                                                    messageCreateEvent.getMessage().addReaction("❌");
                                                }
                                                messageCreateEvent.getChannel().sendMessage("<@" + messageCreateEvent.getMessageAuthor().getId() + "> " + reply);
                                            } catch (Throwable e) {
                                                e.printStackTrace();
                                                Log.info(e);
                                            }
                                        });
                                        return;
                                    }
                                }
                                messageCreateEvent.getMessage().addReaction("❌");
                                messageCreateEvent.getChannel().sendMessage("<@" + messageCreateEvent.getMessageAuthor().getId() + "> " + String.format(invalidMapReply, mapHasInvalidName));
                            }
                        }
                    }
                } else {
                    Log.err("JSON data didn't contain map submissions settings!");
                }
            }
            private String valiadateMap(Map map, MapReader.Mtile[][] tiles, String mode){
                if(mode.){

                }
                if(tiles.length == 0) return mapIsInvalid;
                int mapSize = tiles.length * tiles[0].length;
                if(mapSize < settings.getInt("minimumMapArea")) return mapTooSmall;
                int buildableTiles = 0;
                for(MapReader.Mtile[] row: tiles){
                    for(MapReader.Mtile tile: row){
                        if(tile.wall == Blocks.air){
                            Floor floor = ((Floor)tile.floor);
                            if(floor.isLiquid){
                                if(!floor.isDeep()){
                                    ++buildableTiles;
                                }
                            } else {
                                if(!floor.isStatic()){
                                    ++buildableTiles;
                                }
                            }
                        }
                    }
                }
                float ratio = (float)buildableTiles / (float)mapSize;
                //Log.info((ratio * 100f) + "% of tiles are buildable.");
                if(ratio < settings.getFloat("minimumBuildableArea")) return mapUnderBuildableThreshold;
                return mapIsValid;
            }
        });
    }
}
