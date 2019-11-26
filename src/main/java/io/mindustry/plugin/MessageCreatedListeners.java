package io.mindustry.plugin;

import io.anuke.arc.files.FileHandle;
import io.anuke.arc.files.FileHandleStream;
import io.anuke.arc.util.Log;
import io.anuke.mindustry.core.GameState;
import io.anuke.mindustry.core.World;
import io.anuke.mindustry.game.Gamemode;
import io.anuke.mindustry.io.SaveIO;
import io.anuke.mindustry.maps.Map;
import io.anuke.mindustry.world.Tile;
import io.mindustry.plugin.discordcommands.DiscordCommands;
import io.mindustry.plugin.discordcommands.MessageCreatedListener;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.event.message.MessageCreateEvent;
import org.json.JSONObject;

import java.io.*;
import java.util.zip.InflaterInputStream;

public class MessageCreatedListeners {
    private final JSONObject data;
    private static final String mapSubmissionChannelIDKey = "mapSubmission_channel_id";

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
            private final String invalidMapReply = "%s! Please check pinned messages.";
            private final String[] validModes = new String[] {
                    "survival",
                    "attack",
                    "sandbox",
                    "pvp"
            };
            @Override
            public void run(MessageCreateEvent messageCreateEvent) {
                if(data.has(mapSubmissionChannelIDKey)){
                    long rawID = data.getLong(mapSubmissionChannelIDKey);
                    if(rawID == messageCreateEvent.getChannel().getId()){
                        for (MessageAttachment attachment: messageCreateEvent.getMessageAttachments()) {
                            String fileName = attachment.getFileName();
                            if(fileName.endsWith(".msav")){
                                for(String prefix: validModes){
                                    if(fileName.startsWith(prefix + '_')){
                                        attachment.downloadAsByteArray().thenAccept(data -> {
                                            String reply = "";
                                            Map map = new Map(new VirtualFileHandle(data), 1, 1, null, true);
                                            World world = new World();
                                            world.loadMap(map);
                                            reply = valiadateMap(world);
                                            if(reply.equals(mapIsValid)){
                                                messageCreateEvent.getMessage().addReaction("✅");
                                            } else {
                                                reply = String.format(invalidMapReply, reply);
                                                messageCreateEvent.getMessage().addReaction("❌");
                                            }
                                            messageCreateEvent.getChannel().sendMessage("<@" + messageCreateEvent.getMessageAuthor().getId() + "> " + reply);
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
                    Log.err("JSON data didn't contain map submissions channel ID!");
                }
            }
            private String valiadateMap(World world){
                if(world.isInvalidMap()) return mapIsInvalid;
                if(world.width() * world.height() < 90000) return mapTooSmall;
                float buildableTiles = 0;
                float unbuildableTiles = 0;
                Tile[][] tiles = world.getTiles();
                for(Tile[] row: tiles){
                    for(Tile tile: row){
                        if(tile.floor().isBuildable()){
                            buildableTiles += 1;
                        }
                        unbuildableTiles += 1;
                    }
                }
                float ratio = buildableTiles / unbuildableTiles;
                if(ratio < 0.5) return mapUnderBuildableThreshold;
                return mapIsValid;
            }
        });
    }
}
