package edu.uml.cs.isense.riverwalk.dialogs;

import java.io.IOException;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/** A basic Camera preview class */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
    private SurfaceHolder mHolder;
    private Camera mCamera;

    @SuppressWarnings("deprecation")
	public CameraPreview(Context context, Camera camera) {
        super(context);
        mCamera = camera;
        
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        
        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
    	
    	// Set android picture size (continuous pictures only)
    	setBestPictureSize();
    	
    	if (holder == null){
    		Log.d("surfaceCreated", "holder is null");
    	}
    	try {
            if (mCamera != null) {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
            }
        } catch (IOException exception) {
            Log.e("CameraPreview", exception.getMessage());
        }
    }

    /**
     * Try to get the best picture size with width or height 
     * no greater than 1024.
     */
    private void setBestPictureSize() {
    	
    	int mHeight = 0, mWidth = 0;
    	
    	Parameters parameters = mCamera.getParameters();

    	for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
    		int h = size.height, w = size.width;

    		// If this height/width is better, use it
    		if (w * h > mHeight * mWidth) {
    			mHeight = h;
    			mWidth  = w;
    			continue;
    		}
    	}
    	// Finally, set our camera to use the best supported size we found
        parameters.setPictureSize(mWidth, mHeight);
    	mCamera.setParameters(parameters);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    	mCamera.stopPreview();
        mCamera.setPreviewCallback(null); 
		mCamera.release();
		mCamera = null;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (mHolder.getSurface() == null){
          // preview surface does not exist
          return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e){
          // ignore: tried to stop a non-existent preview
        }
        
        // start preview with new settings
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();

        } catch (Exception e){
            Log.d("CameraPreview", "Error starting camera preview: " + e.getMessage());
        }

    	
    }
}