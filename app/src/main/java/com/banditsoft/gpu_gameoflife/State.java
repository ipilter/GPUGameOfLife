package com.banditsoft.gpu_gameoflife;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;

/**
 * Created by ipilter on 30/03/2017.
 */

public class State implements Serializable {
    private static final long serialVersionUID = -2833423545634569113L;

    public int width;
    public int height;
    transient public ByteBuffer grid;

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeInt(grid.capacity());
        out.write(grid.array());
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        int bufferSize = in.readInt();
        byte[] buffer = new byte[bufferSize];
        in.read(buffer, 0, bufferSize);
        this.grid = ByteBuffer.wrap(buffer, 0, bufferSize);
        grid.position(0);
    }
}
