package io.mindustry.plugin;

import io.anuke.arc.files.FileHandle;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class VirtualFileHandle extends FileHandle {
    private byte[] data;
    public VirtualFileHandle(byte[] data){
        this.data = data;
    }
    @Override
    public InputStream read(){
        return new ByteArrayInputStream(data);
    }
    @Override
    public BufferedInputStream read(int bufferSize){
        return new BufferedInputStream(this.read(), bufferSize);
    }
}
