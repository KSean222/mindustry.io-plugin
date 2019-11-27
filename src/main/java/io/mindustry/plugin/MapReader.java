package io.mindustry.plugin;

import io.anuke.arc.collection.*;
import io.anuke.arc.func.*;
import io.anuke.mindustry.content.*;
import io.anuke.mindustry.ctype.*;
import io.anuke.mindustry.type.*;
import io.anuke.mindustry.world.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import static io.anuke.mindustry.Vars.*;

public class MapReader{
    public static final byte[] mapHeader = {77, 83, 65, 86};
    public MapReader(){
    }

    public Map parseMap(InputStream in) throws IOException{
        Map out = new Map();
        try(InflaterInputStream inf = new InflaterInputStream(in); DataInputStream stream = new DataInputStream(inf)){
            readHeader(stream);
            out.version = stream.readInt();
            readMap(stream, out);
        }
        return out;
    }

    void readMap(DataInputStream stream, Map out) throws IOException{
        //meta
        region(stream);
        StringMap map = new StringMap();
        short size = stream.readShort();
        for(int i = 0; i < size; i++){
            map.put(stream.readUTF(), stream.readUTF());
        }

        out.name = map.get("name", "Unknown");
        out.author = map.get("author");
        out.description = map.get("description");
        out.tags = map;

        //content
        region(stream);
        byte mapped = stream.readByte();

        MappableContent[][] cmap = new MappableContent[ContentType.values().length][0];

        for(int i = 0; i < mapped; i++){
            ContentType type = ContentType.values()[stream.readByte()];
            short total = stream.readShort();
            cmap[type.ordinal()] = new MappableContent[total];

            for(int j = 0; j < total; j++){
                String name = stream.readUTF();
                cmap[type.ordinal()][j] = content.getByName(type, name);
            }
        }

        //map
        region(stream);
        Mtile[][] tiles = readMapData(stream, cmap);
        out.tiles = tiles;
    }

    Mtile[][] readMapData(DataInputStream stream, MappableContent[][] cmap) throws IOException{
        int width = stream.readUnsignedShort();
        int height = stream.readUnsignedShort();

        Func<Short, Block> mapper = i -> (Block)cmap[ContentType.block.ordinal()][i];

        Mtile[][] tiles = new Mtile[width][height];

        //read floor and create tiles first
        for(int i = 0; i < width * height; i++){
            int x = i % width, y = i / width;
            short floorid = stream.readShort();
            short oreid = stream.readShort();
            int consecutives = stream.readUnsignedByte();

            tiles[x][y] = new Mtile(mapper.get(floorid), mapper.get(oreid), Blocks.air);

            for(int j = i + 1; j < i + 1 + consecutives; j++){
                int newx = j % width, newy = j / width;
                tiles[newx][newy] = new Mtile(mapper.get(floorid), mapper.get(oreid), Blocks.air);
            }

            i += consecutives;
        }

        //read blocks
        for(int i = 0; i < width * height; i++){
            int x = i % width, y = i / width;
            Block block = mapper.get(stream.readShort());
            Mtile tile = tiles[x][y];
            tile.wall = block;

            if(block.hasEntity()){
                int length = stream.readUnsignedShort();
                stream.skipBytes(length);
            }else{
                int consecutives = stream.readUnsignedByte();

                for(int j = i + 1; j < i + 1 + consecutives; j++){
                    int newx = j % width, newy = j / width;
                    tiles[newx][newy].wall = block;
                }

                i += consecutives;
            }
        }

        return tiles;
    }

    protected void region(DataInput stream) throws IOException{
        readChunk(stream, false);
    }

    protected int readChunk(DataInput input, boolean isByte) throws IOException{
        return isByte ? input.readUnsignedShort() : input.readInt();
    }

    public static void readHeader(DataInput input) throws IOException{
        byte[] bytes = new byte[mapHeader.length];
        input.readFully(bytes);
        if(!Arrays.equals(bytes, mapHeader)){
            throw new IOException("Incorrect header! Expecting: " + Arrays.toString(mapHeader) + "; Actual: " + Arrays.toString(bytes));
        }
    }

    public static class Map{
        public String name, author, description;
        public ObjectMap<String, String> tags = new ObjectMap<>();
        public int version;
        public Mtile[][] tiles;
    }

    protected static class Mtile{
        Block floor, ore, wall;

        public Mtile(Block floor, Block ore, Block wall){
            this.floor = floor;
            this.ore = ore;
            this.wall = wall;
        }
    }
}