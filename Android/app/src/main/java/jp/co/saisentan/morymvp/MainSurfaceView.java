package jp.co.saisentan.morymvp;

import android.app.Activity;
import android.content.Context;

import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class MainSurfaceView extends SurfaceView implements SurfaceHolder.Callback{
    private final static String TAG = "MainSurfaceView";
    private SurfaceHolder mHolder;
    private MediaRecorder mRecorder;
    private Camera mCamera;
    private String mPath;
    private Context mContext;
    private int mCameraid = 0;// 0 for back camera
    private int mWidth = 640, mHeight = 480;
    private int mMaxfilenum = 10;


    public MainSurfaceView(Context context, SurfaceView sv) {
        super(context);
        mHolder = sv.getHolder();
        mHolder.addCallback(this);
        mContext = context;
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceholder) {}

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        startPreview();
        startVideoRecording();
        Log.d(TAG, "Surface Width:" + String.valueOf(width) + ", Surface Height:" + String.valueOf(height));
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceholder) {}

    public void onPause() {
        stopVideoRecording();
        releaseCamera();
        MediaScannerConnection.scanFile(mContext, new String[] { mPath }, null, null);
    }

    public void startVideoRecording(){
        delete_mp4_file();

        mCamera.unlock();
        mRecorder = new MediaRecorder();
        mRecorder.setCamera(mCamera);
        mRecorder.setOrientationHint(getCameraDisplayOrientation());
        mRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        CamcorderProfile camcorderProfile = CamcorderProfile.get(mCameraid, CamcorderProfile.QUALITY_HIGH);
        mRecorder.setProfile(camcorderProfile);
        mPath = getOutputMediaFile().toString();
        mRecorder.setOutputFile(mPath);
        mRecorder.setVideoSize(mWidth, mHeight);
        try {
            mRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mRecorder.start();
    }

    public void stopVideoRecording(){
        if (mRecorder != null) {
            mRecorder.stop();
            releaseMediaRecorder();
        }
    }

    private void releaseMediaRecorder(){
        if (mRecorder != null) {
            mRecorder.reset();
            mRecorder.release();
            mRecorder = null;
            try {
                mCamera.reconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void startPreview() {
        mCamera = Camera.open(mCameraid);
        try {
            mCamera.setPreviewDisplay(mHolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.setDisplayOrientation(getCameraDisplayOrientation());
        //ビデオ向けの最適化
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setRecordingHint(true);
        mCamera.setParameters(parameters);

        updateCameraParameters();
        startContinuousAutoFocus();

        mCamera.startPreview();
    }

    private void releaseCamera(){
        if (mCamera != null){
            mCamera.release();
            mCamera = null;
        }
    }

    public int getCameraDisplayOrientation() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(mCameraid, info);
        int rotation = ((Activity)getContext()).getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }

    private static File getOutputMediaFile(){
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "MORY");
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("MORY", "failed to create directory");
                return null;
            }
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile = new File(mediaStorageDir.getPath() + File.separator + "MORY_"+ timeStamp + ".mp4");
        return mediaFile;
    }

    private void updateCameraParameters() {
        Camera.Parameters parameters = mCamera.getParameters();
        Log.d(TAG, "updateCameraParameters: " + parameters.flatten());

        Camera.Size previewSize = parameters.getPreviewSize();
        Log.d(TAG, "PreviewSize default: " +" / width-" + previewSize.width + " / height-" + previewSize.height);

        List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
        for (int i = 0; i < supportedPreviewSizes.size(); i++) {
            Camera.Size supportedPreviewSize = supportedPreviewSizes.get(i);
            Log.d(TAG, "PreviewSize : " + i + " / width-" + supportedPreviewSize.width + " / height-" + supportedPreviewSize.height);
        }
        parameters.setPreviewSize(mWidth, mHeight);//プレビューのサイズセット

        List<String> supportedFocusModes = parameters.getSupportedFocusModes();
        for (int i = 0; i < supportedFocusModes.size(); i++) {
            Log.d(TAG, "SupportedFocusModes: " + supportedFocusModes.get(i));
        }
        Log.d(TAG, "FocusMode: " + parameters.getFocusMode());

        mCamera.setParameters(parameters);
    }

    /// Auto Focus ///
    private Camera.AutoFocusMoveCallback mAutoFocusMoveCallback = new Camera.AutoFocusMoveCallback() {
        @Override
        public void onAutoFocusMoving(boolean start, Camera camera) {
            Log.d(TAG, "onAutoFocusMoving: " + start);
        }
    };

    public void startContinuousAutoFocus() {
        Camera.Parameters parameters = mCamera.getParameters();
        if(parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            mCamera.setParameters(parameters);
            mCamera.setAutoFocusMoveCallback(mAutoFocusMoveCallback);
        }
    }

    private void delete_mp4_file(){
        File[] mory_files;
        int filenum = 0;

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "MORY");
        mory_files = mediaStorageDir.listFiles();
        Arrays.sort(mory_files);
        for(int i = 0; i < mory_files.length; i++){
            MediaScannerConnection.scanFile(mContext, new String[] { mory_files[i].toString() }, null, null);//フリーズ時のスキャン漏れ対策
            if(mory_files[i].isFile() && mory_files[i].getName().endsWith(".mp4")){
                filenum++;
                Log.d(TAG,  mory_files[i].getName());
            }
        }
        Log.d(TAG,  "current file num : " + String.valueOf(filenum));
        if(mMaxfilenum <= filenum){
            for(int i = 0; i < mory_files.length; i++){
                if(mory_files[i].isFile() && mory_files[i].getName().endsWith(".mp4")){
                    String dfile = mory_files[i].toString();
                    mory_files[i].delete();
                    MediaScannerConnection.scanFile(mContext, new String[] { dfile }, null, null);
                    Log.d(TAG,  "Deleted : " + dfile);
                    filenum--;
                    if(filenum < mMaxfilenum) break;
                }
            }
        }
    }

}
