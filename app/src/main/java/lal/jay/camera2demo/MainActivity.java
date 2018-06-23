package lal.jay.camera2demo;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private TextureView textureView;
    private TextureView.SurfaceTextureListener textureViewListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            //Surface Texture is Available
            //width x height -> 1080 x 1860 for Redmi Note 4

            //Setup Camera now since the SurfaceTexture is available
            setupCamera(width,height);
            //Toast.makeText(getApplicationContext(),"SurfaceCameraId = "+cameraId,Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    //To hold reference to the Camera Device
    private CameraDevice cameraDevice;

    //Listener to the Camera Device

    private CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int i) {
            camera.close();
            cameraDevice = null;
        }
    };

    private String cameraId;

    private void setupCamera(int width, int height)
    {
        CameraManager camManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try{
            for(String camId: camManager.getCameraIdList())
            {
                CameraCharacteristics camChars = camManager.getCameraCharacteristics(camId);

                if(camChars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK)
                {
                    //Setup the global cameraId to this camId
                    cameraId = camId;

                    //Adjust Sensor to device orientation
                    int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
                    int totalRotation = sensorToDeviceRotation(camChars,deviceOrientation);

                    boolean swapRotation = totalRotation == 90 || totalRotation == 270;

                    int rotatedWidth = width;
                    int rotatedHeight = height;

                    if (swapRotation)
                    {
                        rotatedWidth = height;
                        rotatedHeight = width;
                    }
                    return;
                }

            }
        } catch (CameraAccessException e ){
            e.printStackTrace();
        }

    }

    // We need to take the camera Loading and stuff off the UI thread
    // So we create a background thread and a handler for it..

    private HandlerThread bgThread;
    private Handler bgThreadHandler;


    private void startBgThread()
    {
        bgThread = new HandlerThread("BackgroundThread");
        bgThread.start();

        bgThreadHandler = new Handler(bgThread.getLooper());
    }

    private void stopBgThread()
    {
        bgThread.quitSafely();

        try {
            bgThread.join();

        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        bgThread = null;
        bgThreadHandler = null;
    }


    private static SparseIntArray ORIENTATIONS = new SparseIntArray();
    static{
        ORIENTATIONS.append(Surface.ROTATION_0,0);
        ORIENTATIONS.append(Surface.ROTATION_90,90);
        ORIENTATIONS.append(Surface.ROTATION_180,180);
        ORIENTATIONS.append(Surface.ROTATION_270,270);
    }

    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceOrientation)
    {
        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        //convert deviceOrientation to degrees
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);

        return deviceOrientation;

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Bind textureView variable with layout textureView
        textureView = (TextureView)findViewById(R.id.textureView);

    }

    @Override
    protected void onResume() {
        super.onResume();

        startBgThread();

        //if textureView is not available set a listener that tells us when it's available
        if(!textureView.isAvailable())
            textureView.setSurfaceTextureListener(textureViewListener);

        //If TextureView is Available
        else{
            setupCamera(textureView.getWidth(), textureView.getHeight());
//            Toast.makeText(getApplicationContext(), "CameraId = " + cameraId, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        //Free up resources when the app is paused..
        closeCamera();

        stopBgThread();

        super.onPause();
    }

    //To close the Camera and free up resources
    private void closeCamera() {
        if(cameraDevice!=null)
        {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        //Get the decorView

        View decorView = getWindow().getDecorView();

        //If the app is brought into focus..
        if(hasFocus)
        {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }

    }


}
