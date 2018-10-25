package com.banditsoft.gpu_gameoflife;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PointF;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;
import org.jetbrains.annotations.Nullable;

import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.egl.EGLConfig;

/**
 * Created by ipilter on 22/03/2017.
 */

public class GOLEngine implements GLSurfaceView.Renderer {
    private static final int COORDS_PER_VERTEX = 3;
    private static final int COORDS_PER_TEXEL = 2;
    static final int BYTES_PER_VERTEX = 4;
    static final int BYTES_PER_INDEX = 2;
    static final int CHANNELS_PER_PIXEL = 3;
    static final int MAX_TEXTURE_SIZE = 4096 / 1;
    private final int mVertexStride = COORDS_PER_VERTEX * BYTES_PER_VERTEX;

    private int mRendererProgram;
    private int mSimulatorProgram;
    private int mFrontTextureId;
    private int mBackTextureId;
    private int mFrameBufferId;
    private int mRenderBufferId;
    private int mWidth;
    private int mHeight;

    private float mRatio;
    private final float[] mProjectionMatrix = new float[16];
    private final float[] mViewMatrix = new float[16];
    private final float[] mVPMatrix = new float[16];

    private final float mSquareCoords[] = {
            -1.0f, -1.0f, 0.0f,
            1.0f, -1.0f, 0.0f,
            1.0f, 1.0f, 0.0f,
            -1.0f, 1.0f, 0.0f};

    private final float mTextureCoords[] = {
            0.0f, 0.0f,
            1.0f, 0.0f,
            1.0f, 1.0f,
            0.0f, 1.0f};

    // initial rules are the standard game of life rules: dead = 8, live = 12
    private int mDecideData[] = {
          //0  1  2  3  4  5  6  7  8   // living neighbor count
            0, 0, 0, 1, 0, 0, 0, 0, 0,  // dead cell new state
            0, 0, 1, 1, 0, 0, 0, 0, 0}; // live cell new state

    private final short mIndices[] = {0, 1, 3, 3, 1, 2};

    private final FloatBuffer mVertexBuffer;
    private final ShortBuffer mIndexBuffer;
    private final FloatBuffer mTexelBuffer;

    private int mGridWidth;
    private int mGridHeight;
    private ByteBuffer mInitialState;

    private int mNoiseWidth;
    private int mNoiseHeight;
    private ByteBuffer mNoiseBuffer;

    private float mModelSpaceHalfSize = 1.0f;
    private int mWorldScale = 2;
    private int mSeedCount = 1;

    private String mRendererVertexShader;
    private String mRendererFragmentShader;
    private String mSimulatorVertexShader;
    private String mSimulatorFragmentShader;

    public GOLEngine(Context context) {
        setRules(8, 12);

        ByteBuffer bb = ByteBuffer.allocateDirect(mSquareCoords.length * BYTES_PER_VERTEX);
        bb.order(ByteOrder.nativeOrder());
        mVertexBuffer = bb.asFloatBuffer();
        mVertexBuffer.put(mSquareCoords);
        mVertexBuffer.position(0);

        ByteBuffer dlb = ByteBuffer.allocateDirect(mIndices.length * BYTES_PER_INDEX);
        dlb.order(ByteOrder.nativeOrder());
        mIndexBuffer = dlb.asShortBuffer();
        mIndexBuffer.put(mIndices);
        mIndexBuffer.position(0);

        ByteBuffer tbb = ByteBuffer.allocateDirect(mTextureCoords.length * BYTES_PER_VERTEX);
        tbb.order(ByteOrder.nativeOrder());
        mTexelBuffer = tbb.asFloatBuffer();
        mTexelBuffer.put(mTextureCoords);
        mTexelBuffer.position(0);

        Matrix.setIdentityM(mViewMatrix, 0);
        Matrix.setIdentityM(mProjectionMatrix, 0);
        Matrix.setIdentityM(mVPMatrix, 0);

        mGridWidth = MAX_TEXTURE_SIZE / mWorldScale;
        mGridHeight = MAX_TEXTURE_SIZE / mWorldScale;

        try
        {
            InputStream iStream = context.getAssets().open("InitialState.png");
            Bitmap bitmap = BitmapFactory.decodeStream(iStream);
            setInitialState(bitmap);
        }
        catch (IOException ex)
        {
            Log.e("GOLEngine", ex.getMessage());
            return;
        }

        try
        {
            InputStream iStream = context.getAssets().open("Noise.png");
            Bitmap bitmap = BitmapFactory.decodeStream(iStream);
            setNoise(bitmap);
        }
        catch (IOException ex)
        {
            Log.e("GOLEngine", ex.getMessage());
            return;
        }

        mRendererVertexShader = loadShader(context.getAssets(), "renderer.vsh");
        mRendererFragmentShader = loadShader(context.getAssets(), "renderer.fsh");
        mSimulatorVertexShader = loadShader(context.getAssets(), "simulator.vsh");
        mSimulatorFragmentShader = loadShader(context.getAssets(), "simulator.fsh");
    }

    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        mFrontTextureId = createTexture(mGridWidth, mGridHeight, mInitialState);
        mBackTextureId = createTexture(mGridWidth, mGridHeight, null);

        mRendererProgram = createShaderProgram(mRendererVertexShader, mRendererFragmentShader);
        mSimulatorProgram = createShaderProgram(mSimulatorVertexShader, mSimulatorFragmentShader);

        mFrameBufferId = createFrameBuffer();
        mRenderBufferId = createRenderBuffer();

        checkGLError("onSurfaceCreated");
    }

    public void onDrawFrame(GL10 unused)
    {
        render();
    }

    public void onSurfaceChanged(GL10 unused, int width, int height)
    {
        mWidth  = width;
        mHeight =  height;
        mRatio = (float) mWidth / mHeight;

        GLES20.glViewport(0, 0, mWidth, mHeight);
        Matrix.orthoM(mProjectionMatrix, 0, -mModelSpaceHalfSize * mRatio, mModelSpaceHalfSize * mRatio, -mModelSpaceHalfSize, mModelSpaceHalfSize, -1, 1);

        updateModelViewProjection();
        checkGLError("onSurfaceChanged");
    }

    public PointF map(PointF screenSpacePoint)
    {
        float[] normalizedScreenSpacePoint = new float[4];
        normalizedScreenSpacePoint[0] = 2.0f * screenSpacePoint.x / mWidth - 1.0f;
        normalizedScreenSpacePoint[1] = -2.0f * screenSpacePoint.y / mHeight + 1.0f;
        normalizedScreenSpacePoint[3] = 1.0f;

        float[] mvpInv = new float[16];
        Matrix.invertM(mvpInv, 0, mVPMatrix, 0);

        float modelSpacePoint[] = new float[4];
        Matrix.multiplyMV(modelSpacePoint, 0, mvpInv, 0, normalizedScreenSpacePoint, 0);
        return new PointF(modelSpacePoint[0], modelSpacePoint[1]);
    }

    public void pan(PointF delta) {
        Matrix.translateM(mViewMatrix, 0, delta.x, delta.y, 0.0f);
        updateModelViewProjection();
    }

    public void scale(float scale, PointF focusPoint) {
        Matrix.translateM(mViewMatrix, 0, mViewMatrix, 0, focusPoint.x, focusPoint.y, 0.0f);
        Matrix.scaleM(mViewMatrix, 0, mViewMatrix, 0, scale, scale, 1.0f);
        Matrix.translateM(mViewMatrix, 0, mViewMatrix, 0, -focusPoint.x, -focusPoint.y, 0.0f);
        updateModelViewProjection();
    }

    public State getState()
    {
        State state = new State();
        state.width = mGridWidth;
        state.height = mGridHeight;
        state.grid = getGridState();
        return state;
    }

    public void setState(State state)
    {
        mGridWidth = state.width;
        mGridHeight = state.height;
        mInitialState = state.grid;
        mInitialState.position(0);
    }

    public void setRules(int deadRule, int liveRule)
    {
        int bitCount = 9; // 0, 1 .. 8 living neighbour
        int currentState = 0;
        int currentRule = deadRule;
        for(int ruleIdx = 0; ruleIdx < 2; ++ruleIdx) {
            for (int bitIdx = 0; bitIdx < bitCount; ++bitIdx) {
                mDecideData[currentState * bitCount + bitIdx] = (currentRule >> bitIdx) & 1;
            }
            currentRule = liveRule;
            ++currentState;
        }
    }

    public void setInitialState(Bitmap bitmap)
    {
        int size = mGridWidth * mGridHeight * CHANNELS_PER_PIXEL;
        byte initialData[] = new byte[size];
        Arrays.fill( initialData, (byte)0 );

        int wX = bitmap.getWidth();
        int wY = bitmap.getHeight();
        int sX = (mGridWidth / 2) - (wX / 2);
        int sY = (mGridHeight / 2) - (wY / 2);
        for (int x = 0; x < wX; ++x) {
            for (int y = 0; y < wY; ++y) {
                if(Color.red(bitmap.getPixel(x, wY - 1 - y)) == 255)
                {
                    int offset = (sX + x) * CHANNELS_PER_PIXEL + (sY + y) * mGridWidth * CHANNELS_PER_PIXEL;
                    initialData[offset] = (byte)255;
                    initialData[offset + 1] = (byte)255;
                    initialData[offset + 2] = (byte)255;
                }
            }
        }

        mInitialState = ByteBuffer.allocateDirect(size);
        mInitialState.order(ByteOrder.nativeOrder());
        mInitialState.put(initialData);
        mInitialState.position(0);
    }

    public void reset()
    {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFrontTextureId);
        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, mGridWidth, mGridHeight, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, mInitialState);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    public void addNoise()
    {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFrontTextureId);
        for (int c = 0; c < mSeedCount; ++c)
        {
            int x = (int)(Math.random() * mGridWidth - mNoiseWidth);
            int y = (int)(Math.random() * mGridHeight - mNoiseHeight);
            GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, x, y, mNoiseWidth, mNoiseHeight, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, mNoiseBuffer);
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    public void simulate()
    {
        swapTextures();

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBufferId);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mBackTextureId);
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, mRenderBufferId);

        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, mGridWidth, mGridHeight);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, mFrontTextureId, 0);
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, mRenderBufferId);
        checkFramebufferStatus();

        GLES20.glViewport(0, 0, mGridWidth, mGridHeight);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(mSimulatorProgram);

        int positionHandle = GLES20.glGetAttribLocation(mSimulatorProgram, "aPosition");
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, mVertexStride, mVertexBuffer);

        int textureSamplerHandle = GLES20.glGetUniformLocation(mSimulatorProgram, "uTexture");
        GLES20.glUniform1i(textureSamplerHandle, 0);

        int scaleHandle = GLES20.glGetUniformLocation(mSimulatorProgram, "uScale");
        GLES20.glUniform2f(scaleHandle, mGridWidth, mGridHeight);

        int decideDataHandle = GLES20.glGetUniformLocation(mSimulatorProgram, "uDecideData");
        GLES20.glUniform1iv(decideDataHandle, mDecideData.length, mDecideData, 0);

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mIndices.length, GLES20.GL_UNSIGNED_SHORT, mIndexBuffer);

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, mWidth, mHeight);
    }

    private void render()
    {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(mRendererProgram);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFrontTextureId);

        int mvpMatrixHandle = GLES20.glGetUniformLocation(mRendererProgram, "uMVPMatrix");
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mVPMatrix, 0);

        int texelHandle = GLES20.glGetAttribLocation(mRendererProgram, "aTexel");
        GLES20.glEnableVertexAttribArray ( texelHandle );
        GLES20.glVertexAttribPointer(texelHandle, COORDS_PER_TEXEL, GLES20.GL_FLOAT, false, 0, mTexelBuffer);

        int positionHandle = GLES20.glGetAttribLocation(mRendererProgram, "aPosition");
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, mVertexStride, mVertexBuffer);

        int textureSamplerHandle = GLES20.glGetUniformLocation(mRendererProgram, "uTexture");
        GLES20.glUniform1i(textureSamplerHandle, 0);

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mIndices.length, GLES20.GL_UNSIGNED_SHORT, mIndexBuffer);

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    private ByteBuffer getGridState()
    {
        int size = mGridWidth * mGridHeight * CHANNELS_PER_PIXEL;
        ByteBuffer pixelBuffer = ByteBuffer.allocateDirect(size);
        pixelBuffer.order(ByteOrder.nativeOrder());

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFrontTextureId);
        GLES20.glReadPixels(0, 0, mGridWidth, mGridHeight, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, pixelBuffer);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        pixelBuffer.position(0);
        checkGLError("getCurrentState");
        return pixelBuffer;
    }

    private void setNoise(Bitmap bitmap)
    {
        mNoiseWidth = bitmap.getWidth();
        mNoiseHeight = bitmap.getHeight();
        int size = mNoiseWidth * mNoiseHeight * CHANNELS_PER_PIXEL;
        byte noiseData[] = new byte[size];
        Arrays.fill( noiseData, (byte)0 );

        for (int x = 0; x < mNoiseWidth; ++x) {
            for (int y = 0; y < mNoiseHeight; ++y) {
                if(Color.red(bitmap.getPixel(x, mNoiseHeight - 1 - y)) == 255)
                {
                    int offset = x * CHANNELS_PER_PIXEL + y * mNoiseWidth * CHANNELS_PER_PIXEL;
                    noiseData[offset] = (byte)255;
                    noiseData[offset + 1] = (byte)255;
                    noiseData[offset + 2] = (byte)255;
                }
            }
        }

        mNoiseBuffer = ByteBuffer.allocateDirect(size);
        mNoiseBuffer.order(ByteOrder.nativeOrder());
        mNoiseBuffer.put(noiseData);
        mNoiseBuffer.position(0);
    }

    private void updateModelViewProjection()
    {
        Matrix.multiplyMM(mVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);
    }

    private void swapTextures()
    {
        int temp = mFrontTextureId;
        mFrontTextureId = mBackTextureId;
        mBackTextureId = temp;
    }

    private static int createFrameBuffer()
    {
        int[] ids = new int[1];
        GLES20.glGenFramebuffers(1, ids, 0);
        checkGLError("createFrameBuffer");
        return ids[0];
    }

    private static int createRenderBuffer()
    {
        int[] ids = new int[1];
        GLES20.glGenRenderbuffers(1, ids, 0);
        checkGLError("createRenderBuffer");
        return ids[0];
    }

    private static int createTexture(int width, int height, @Nullable ByteBuffer buffer)
    {
        int[] textureIds = new int[1];
        GLES20.glGenTextures(1, textureIds, 0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, width, height, 0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, buffer);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        checkGLError("createTexture");
        return textureIds[0];
    }

    private static int createShaderProgram(String vertexShader, String fragmentShader)
    {
        int programId = GLES20.glCreateProgram();
        GLES20.glAttachShader(programId, createShader(GLES20.GL_VERTEX_SHADER, vertexShader));
        GLES20.glAttachShader(programId, createShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader));
        GLES20.glLinkProgram(programId);
        checkShaderLinkStatus(programId);
        checkGLError("createShaderProgram");
        return programId;
    }

    private static int createShader(int type, String shader){
        int shaderId = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shaderId, shader);
        GLES20.glCompileShader(shaderId);
        checkShaderCompileStatus(shaderId);
        checkGLError("loadShader");
        return shaderId;
    }

    private static void checkGLError(String function) {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            Log.e("GOLEngine", "OpenGL Error 0x" + Integer.toHexString(error) + " in function " + function);
        }
    }

    private String loadShader(AssetManager assetManager, String fileName)
    {
        StringBuilder codeBuilder = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open(fileName)));
            String line = reader.readLine();
            while(line != null)
            {
                codeBuilder.append(line);
                line = reader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return codeBuilder.toString();
    }

    private static boolean checkIfContextSupportsExtension(String extension)
    {
        String extensions = " " + GLES20.glGetString(GLES20.GL_EXTENSIONS) + " ";
        return extensions.indexOf(" " + extension + " ") >= 0;
    }

    private static void checkShaderCompileStatus(int shaderId)
    {
        int[] status = new int[1];
        GLES20.glGetShaderiv(shaderId, GLES20.GL_COMPILE_STATUS, status, 0);
        checkGLError("checkShaderCompileStatus");
        if(status[0] == GLES20.GL_FALSE)
        {
            String shaderInfo = GLES20.glGetShaderInfoLog(shaderId);
            Log.e("ShaderCompile", "Shader with id " + shaderId + " not compiled. Status: " + status[0] + ", info: " + shaderInfo);
        }
    }

    private static void checkShaderLinkStatus(int shaderProgramId)
    {
        int[] status = new int[1];
        GLES20.glGetProgramiv(shaderProgramId, GLES20.GL_LINK_STATUS, status, 0);
        checkGLError("checkShaderLinkStatus");
        if (status[0] <= 0) {
            Log.d("ShaderLink","Shader program with id " + shaderProgramId + " not linked. Status: " + status[0]);
        }
    }

    private static void checkFramebufferStatus()
    {
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if(status != GLES20.GL_FRAMEBUFFER_COMPLETE)
        {
            String statusMsg;
            switch (status) {
                case GLES20.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT:
                    statusMsg = "ATTACHMENT: Not all framebuffer attachment points are framebuffer attachment complete.";
                    break;
                case GLES20.GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS:
                    statusMsg = "DIMENSIONS: Not all attached images have the same width and height";
                    break;
                case GLES20.GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT:
                    statusMsg = "MISSING_ATTACHMENT: No images are attached to the framebuffer.";
                    break;
                case GLES20.GL_FRAMEBUFFER_UNSUPPORTED:
                    statusMsg = "UNSUPPORTED: The combination of internal formats of the attached images violates an implementation-dependent set of restrictions.";
                    break;
                default:
                    statusMsg = "Unknown error";
            }
            Log.e("FramebufferStatus", "is not complete. status: 0x" + Integer.toHexString(status) + " - " + statusMsg);
        }
    }
}
