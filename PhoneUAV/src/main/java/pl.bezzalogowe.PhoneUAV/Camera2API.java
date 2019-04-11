package pl.bezzalogowe.PhoneUAV;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class Camera2API implements SurfaceTextureListener {
    private static final String TAG = "camera2";
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAIT_LOCK = 1;
    public HandlerThread camera2thread;
    public Handler camera2handler;
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new
            ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    camera2handler.post(new ImageSaver(reader.acquireLatestImage()));
                }
            };
    public CameraCaptureSession mPreviewCaptureSession;
    public String imageFileName, imageFilePath, videoFilePath;
    /**
     * http://developer.android.com/reference/android/hardware/camera2/package-summary.html
     * https://www.youtube.com/channel/UC4jh7YBBb0UnPIef2NOSJhQ
     */

    MainActivity main;
    CameraManager cameraManager;
    String[] camerasList;
    boolean cameraHasAutoFocus = false;
    boolean torch = false;
    TextureView mTextureView;
    Thread previewThread;
    Size maxResJPEG, maxResMP4;
    String toEmails = null;
    List<String> toEmailList = null;
    CameraDevice mCameraDevice;
    boolean isRecording = false;
    boolean isBusy = false;
    private int mCaptureState = STATE_PREVIEW;
    private ImageReader mImageReader;
    private MediaRecorder mMediaRecorder;
    CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            mMediaRecorder = new MediaRecorder();
            startPreview();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            stopPreview();

            camera.close();
            mCameraDevice = null;
        }
    };
    TextureView.SurfaceTextureListener msurfacetextureviewlistener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            // TODO Auto-generated method stub
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface,
                                                int width, int height) {
            // TODO Auto-generated method stub
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setupCamera();
            connectCamera();
        }
    };
    private CameraCaptureSession mRecordCaptureSession;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private File imageFolder, videoFolder;
    private CameraCaptureSession.CaptureCallback mPreviewCaptureCallback = new
            CameraCaptureSession.CaptureCallback() {
                private void process(CaptureResult captureResult) {
                    switch (mCaptureState) {
                        case STATE_PREVIEW:
                            break;
                        case STATE_WAIT_LOCK:
                            mCaptureState = STATE_PREVIEW;
                            Integer afState = captureResult.get(CaptureResult.CONTROL_AF_STATE);

                            if (cameraHasAutoFocus) {
                                if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                                        afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                                    startStillCaptureRequest();
                                }
                            } else {
                                startStillCaptureRequest();
                            }
                            break;
                    }
                }

                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    process(result);
                }
            };
    private CameraCaptureSession.CaptureCallback mRecordCaptureCallback = new
            CameraCaptureSession.CaptureCallback() {
                private void process(CaptureResult captureResult) {
                    switch (mCaptureState) {
                        case STATE_PREVIEW:
                            // Do nothing
                            break;
                        case STATE_WAIT_LOCK:
                            mCaptureState = STATE_PREVIEW;
                            Integer afState = captureResult.get(CaptureResult.CONTROL_AF_STATE);
                            if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                                    afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                                Log.d(TAG, "camera " + main.cameraID + " AF Locked!");
                                startStillCaptureRequest();
                            }
                            break;
                    }
                }

                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);

                    process(result);
                }
            };

    private static Size pickSizeJPEG(StreamConfigurationMap map) {
        /** https://developer.android.com/reference/android/graphics/ImageFormat#JPEG */
        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
        /* min, last item from the list */
        /* max, first item from the list */
        return sizes[0];
    }

    private static Size pickSizeMP4(StreamConfigurationMap map) {
        /** https://developer.android.com/reference/android/graphics/ImageFormat#YUV_420_888 */
        Size[] sizes = map.getOutputSizes(ImageFormat.YUV_420_888);
        return sizes[0];
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width,
                                          int height) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width,
                                            int height) {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // TODO Auto-generated method stub
    }

    public void captureImage() {
        /**
         * https://www.nigeapptuts.com/android-video-app-still-capture-recording/
         */

        if (!isRecording) {
            if (!isBusy) {
                lockFocus();
            }
        } else {
            //TODO: stop recording, take picture, start recording again
            //main.update.updateConversationHandler.post(new updateVisibilityThread(main.photoButton, View.INVISIBLE));
            //main.update.updateConversationHandler.post(new updateVisibilityThread(main.photoButton, View.VISIBLE));
            Log.d(TAG, "Can't take photo while recording yet!");
        }
    }

    public void setListener(MainActivity argActivity) {
        main = argActivity;
        try {
            mTextureView.setSurfaceTextureListener(msurfacetextureviewlistener);
        } catch (Exception e) {
            Log.d(TAG, e.toString());
            main.logObject.saveComment("error" + e);
        }
    }

    private void printSizes(StreamConfigurationMap map) {
        /**
         * https://developer.android.com/reference/android/graphics/ImageFormat.html
         */

        int[] formats = map.getOutputFormats();
        for (int item : formats) {
            Log.d(TAG, "format: " + item);
            String str = "";
            Size[] sizes = map.getOutputSizes(item);
            for (int i = 0; i < sizes.length; i++) {
                str = str + sizes[i].getWidth() + "×" + sizes[i].getHeight();
                if (i < sizes.length - 1) {
                    str = str + ", ";
                }
            }
            Log.d(TAG, "resolutions: " + str);
        }
    }

    public void setupCamera() {
        try {
            camerasList = cameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            Log.d(TAG, "list: " + e.toString());
        }

        try {
            // When camera ID is not in the cameras array, use primary camera.
            if (Integer.valueOf(main.cameraID) >= (cameraManager.getCameraIdList().length))
                main.cameraID = "0";
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraChar = cameraManager.getCameraCharacteristics(id);

                if (cameraChar.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    Size sensorFront = cameraChar.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                    Log.d(TAG, "camera " + id + " front facing: " +
                            sensorFront.getWidth() + "×" +
                            sensorFront.getHeight());
                }

                if (cameraChar.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    Size sensorBack = cameraChar.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                    Log.d(TAG, "camera " + id + " back facing: " +
                            sensorBack.getWidth() + "×" +
                            sensorBack.getHeight());
                }

                if (cameraChar.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_EXTERNAL) {

                    Log.d(TAG, "camera " + id + " external: " +
                            cameraChar.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE).getWidth() + "×" +
                            cameraChar.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE).getHeight());
                }
            }

            StreamConfigurationMap map = getStreamConfigurationMap();
            // Prints all available picture sizes for all formats.
            printSizes(map);

            // Picks maximum picture size.
            maxResJPEG = pickSizeJPEG(map);
            Log.d(TAG, "picked photo size: " + maxResJPEG.getWidth() + "×" + maxResJPEG.getHeight());

            maxResMP4 = pickSizeMP4(map);
            Log.d(TAG, "picked video size: " + maxResMP4.getWidth() + "×" + maxResMP4.getHeight());

            mImageReader = ImageReader.newInstance(maxResJPEG.getWidth(), maxResJPEG.getHeight(), ImageFormat.JPEG, 1);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, camera2handler);

            /** https://developer.android.com/reference/android/hardware/camera2/CaptureRequest.html#CONTROL_AF_MODE */
            // Checks if camera has autofocus feature.
            int[] afModes = cameraManager.getCameraCharacteristics(main.cameraID).get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);

            Log.d(TAG, "CONTROL_AF_AVAILABLE_MODES:");
            for (int position : afModes) {
                switch (afModes[position]) {
                    case 0:
                        Log.d(TAG, "CONTROL_AF_MODE_OFF (0)");
                        break;
                    case 1:
                        Log.d(TAG, "CONTROL_AF_MODE_AUTO (1)");
                        break;
                    case 2:
                        Log.d(TAG, "CONTROL_AF_MODE_MACRO (2)");
                        break;
                    case 3:
                        Log.d(TAG, "CONTROL_AF_MODE_CONTINUOUS_VIDEO (3)");
                        break;
                    case 4:
                        Log.d(TAG, "CONTROL_AF_MODE_CONTINUOUS_PICTURE (4)");
                        break;
                    case 5:
                        Log.d(TAG, "CONTROL_AF_MODE_EDOF (5)");
                        break;
                    default:
                        Log.d(TAG, String.valueOf(afModes[position]));
                }
            }

            if (afModes.length <= 1 && afModes[0] == 0) {
                cameraHasAutoFocus = false;
            } else {
                cameraHasAutoFocus = true;
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private StreamConfigurationMap getStreamConfigurationMap() throws CameraAccessException {
        StreamConfigurationMap map;
        map = cameraManager.getCameraCharacteristics(main.cameraID).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        return map;
    }

    void connectCamera() {
        try {
            if (Build.VERSION.SDK_INT <= 23) {
                try {
                    cameraManager.openCamera(main.cameraID, mCameraDeviceStateCallback, camera2handler);
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
            } else {
                if (ContextCompat.checkSelfPermission(main, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    cameraManager.openCamera(main.cameraID, mCameraDeviceStateCallback, camera2handler);
                }
                /*
                if(ContextCompat.checkSelfPermission(main, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    cameraManager.openCamera(main.cameraID, mCameraDeviceStateCallback, camera2handler);
                } else {
                    if(shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)) {
                        Toast.makeText(this, "Video app required access to camera", Toast.LENGTH_SHORT).show();
                    }
                    requestPermissions(new String[] {android.Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO
                    }, REQUEST_CAMERA_PERMISSION_RESULT);
                }
                */
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setupMediaRecorder() throws IOException, CameraAccessException {
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(videoFilePath);
        mMediaRecorder.setVideoEncodingBitRate(8000000);
        mMediaRecorder.setVideoFrameRate(30);
        StreamConfigurationMap map;
        map = getStreamConfigurationMap();
        maxResMP4 = pickSizeMP4(map);
        mMediaRecorder.setVideoSize(maxResMP4.getWidth(), maxResMP4.getHeight());
        //mMediaRecorder.setVideoSize(1920, 1080);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setOrientationHint(0);
        mMediaRecorder.prepare();
    }

    /**
     * https://stackoverflow.com/questions/34093508/toggle-flashlight-in-camera2-without-interrupting-preview
     */
    public void turnOnTorch() {
        /** activates torch if it is off */
        if (!torch) {
            try {
                CameraCharacteristics tempChar = cameraManager.getCameraCharacteristics(main.cameraID);
                if (tempChar.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)) {
                    mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                    if (isRecording) {
                        mRecordCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, null);
                    } else {
                        mPreviewCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, camera2handler);
                    }
                }
            } catch (Exception e) {
                Log.d("camera", "couldn't turn on torch: " + e);
            }
        }
        torch = true;
    }

    public void turnOffTorch() {
        /** deactivates torch if it is on */
        if (torch) {
            try {
                mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                if (isRecording) {
                    mRecordCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, null);
                } else {
                    mPreviewCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, camera2handler);
                }
            } catch (Exception e) {
                Log.d("camera", "couldn't turn off torch: " + e);
            }
        }
        torch = false;
    }

    public void sendPhotoByEmail() {
        try {
            String fromEmail = "user@server.pl";
            String fromPassword = "";
            String emailSubject = "temat";
            String emailBody = "attached photo: " + imageFileName;
            new SendMailTask().execute(fromEmail, fromPassword, toEmailList, emailSubject, emailBody, main);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public void startRecord() {
        try {
            createVideoFileName();
            setupMediaRecorder();

            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(176, 144);
            Surface previewSurface = new Surface(surfaceTexture);
            Surface recordSurface = mMediaRecorder.getSurface();
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            mCaptureRequestBuilder.addTarget(previewSurface);
            mCaptureRequestBuilder.addTarget(recordSurface);

            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, recordSurface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            mRecordCaptureSession = session;
                            try {

                                mRecordCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, null);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.d(TAG, "onConfigureFailed: startRecord");
                        }
                    }, null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            mMediaRecorder.start();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            Log.d(TAG, "start error: " + e);
        }

        isRecording = true;
        main.videoButton.setText("\u25A0");

        Thread feedbackCameraThread = new Thread(new Wrap());
        feedbackCameraThread.start();

        Thread torch = new Thread(new setTorchDelayed());
        torch.start();
    }

    public void stopRecord() {
        // Starting the preview prior to stopping recording which should hopefully resolve issues being seen in Samsung devices.
        startPreview();
        try {
            mMediaRecorder.start();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            Log.d(TAG, "error: " + e);
        }

        mMediaRecorder.reset();
        isRecording = false;
        main.videoButton.setText("\u25CF");

        Thread feedbackFeedbackCamera = new Thread(new Wrap());
        feedbackFeedbackCamera.start();
        truncateVideoPath();
        Thread torch = new Thread(new setTorchDelayed());
        torch.start();
    }

    public void startPreview() {
        previewThread = new Thread(new PreviewThread());
        previewThread.start();
    }

    public void stopPreview() {
        if (previewThread != null) {
            try {
                mPreviewCaptureSession.stopRepeating();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            previewThread.interrupt();
            previewThread = null;
        }
    }

    private void startStillCaptureRequest() {
        try {
            if (isRecording) {
                mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);
            } else {
                mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            }
            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
            mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 0);

            CameraCaptureSession.CaptureCallback stillCaptureCallback = new
                    CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                            super.onCaptureStarted(session, request, timestamp, frameNumber);
                            try {
                                isBusy = true;
                                createImageFileName();
                            } catch (IOException e) {
                                e.printStackTrace();
                                Log.d("onCaptureStarted", "error: " + e);
                            }
                        }

                        @Override
                        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                            truncateImagePath();
                            if (toEmailList != null) {
                                if (toEmailList.size() > 0) {
                                    Log.d("SendMailTask", "onCaptureCompleted");
                                    sendPhotoByEmail();
                                }
                            }
                            isBusy = false;
                        }

                        @Override
                        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
                            Log.d("SendMailTask", "Image capture failed.");
                        }
                    };

            if (isRecording) {
                mRecordCaptureSession.capture(mCaptureRequestBuilder.build(), stillCaptureCallback, null);
            } else {
                mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(), stillCaptureCallback, null);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Log.d("startStillCaptureReq.", "error: " + e);
        }
    }

    public void startCameraThread() {
        camera2thread = new HandlerThread("Camera2");
        camera2thread.start();
        camera2handler = new Handler(camera2thread.getLooper());
    }

    public void stopCameraThread() {
        camera2thread.quitSafely();
        try {
            camera2thread.join();
            camera2thread = null;
            camera2handler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void closeActiveCamera() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    String getTimeStamp() {
        Long time = System.currentTimeMillis();
        String dateISO8601 = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(time));
        String timeISO8601 = new java.text.SimpleDateFormat("HHmmss").format(new java.util.Date(time));
        return dateISO8601 + "_" + timeISO8601;
    }

    public void createVideoFolder() {
        /* DIRECTORY_MOVIES */
        File movieFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        videoFolder = new File(movieFile, "PhoneUAVmpeg4");
        if (!videoFolder.exists()) {
            videoFolder.mkdirs();
        }
    }

    public void createImageFolder() {
        /* DIRECTORY_PICTURES */
        File imageFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        imageFolder = new File(imageFile, "PhoneUAVjpeg");
        if (!imageFolder.exists()) {
            imageFolder.mkdirs();
        }
    }

    public File createVideoFileName() throws IOException {
        String timestamp = getTimeStamp();
        File videoFile = null;
        try {
            videoFile = File.createTempFile(timestamp, ".mp4", videoFolder);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(main, e.toString(), Toast.LENGTH_SHORT).show();
        }
        videoFilePath = videoFile.getAbsolutePath();
        return videoFile;
    }

    private File createImageFileName() throws IOException {
        String timestamp = getTimeStamp();
        File imageFile = null;
        try {
            imageFile = File.createTempFile(timestamp, ".jpg", imageFolder);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(main, e.toString(), Toast.LENGTH_SHORT).show();
        }
        imageFilePath = imageFile.getAbsolutePath();
        imageFileName = imageFile.getName();
        return imageFile;
    }

    private void truncateVideoPath() {
        /** Removes trailing number from filename. */
        String shortPath = videoFilePath.replaceFirst("([0-9]){10}(?=\\.)", "");

        /** Truncates filename by fixed number of characters and adds back extension. */
        //String shortName = path.substring(0, path.length() - 14) + ".mp4";

        File from = new File(videoFilePath);
        File to = new File(shortPath);
        from.renameTo(to);

        videoFilePath = shortPath;
    }

    private void truncateImagePath() {
        /** Removes trailing number from filename. */
        String shortPath = imageFilePath.replaceFirst("([0-9]){10}(?=\\.)", "");

        File from = new File(imageFilePath);
        File to = new File(shortPath);
        from.renameTo(to);

        imageFilePath = shortPath;
        imageFileName = to.getName();
    }

    public void lockFocus() {
        mCaptureState = STATE_WAIT_LOCK;
        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        try {
            mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(), mPreviewCaptureCallback, camera2handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Log.d("lockFocus", "error: " + e);
        }
    }

    private class ImageSaver implements Runnable {

        private final Image mImage;

        public ImageSaver(Image image) {
            mImage = image;
        }

        @Override
        public void run() {
            ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);

            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new FileOutputStream(imageFilePath);
                fileOutputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();

                Intent mediaStoreUpdateIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                mediaStoreUpdateIntent.setData(Uri.fromFile(new File(imageFilePath)));
                //sendBroadcast(mediaStoreUpdateIntent);

                if (fileOutputStream != null) {
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    class setTorchDelayed implements Runnable {
        /**
         * Sets torch with delay.
         */

        @Override
        public void run() {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            try {
                if (torch) {
                    CameraCharacteristics tempChar = cameraManager.getCameraCharacteristics(main.cameraID);
                    if (tempChar.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)) {
                        mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                    }
                } else {
                    try {
                        mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                    } catch (Exception e) {
                        Log.d("camera", "torch mode setting error: " + e);
                    }
                }

                if (isRecording) {
                    mRecordCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, null);
                } else {
                    mPreviewCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, camera2handler);
                }
            } catch (Exception e) {
                Log.d("camera", "torch repeating request setting error: " + e);
            }
        }
    }

    class PreviewThread implements Runnable {
        @SuppressWarnings("all")
        public void run() {
            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(176, 144);
            final Surface preview = new Surface(surfaceTexture);

            try {
                mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

            mCaptureRequestBuilder.addTarget(preview);

            CameraCaptureSession.StateCallback callback = new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession session) {
                    mPreviewCaptureSession = session;
                    try {
                        //FIXME: causes crashes when the app run for the first time after re-installation
                        session.setRepeatingRequest(mCaptureRequestBuilder.build(), null, camera2handler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Log.d(TAG, "configuration failed");
                }
            };

            try {
                mCameraDevice.createCaptureSession(Arrays.asList(preview, mImageReader.getSurface()), callback, camera2handler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    /*API >= 23 */
/*
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_CAMERA_PERMISSION_RESULT) {
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(),
                        "Application will not run without camera services", Toast.LENGTH_SHORT).show();
            }
            if(grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(),
                        "Application will not have audio on record", Toast.LENGTH_SHORT).show();
            }
        }
        if(requestCode == REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT) {
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if(mIsRecording || mIsTimelapse) {
                    mIsRecording = true;
                    mRecordImageButton.setImageResource(R.mipmap.btn_video_busy);
                }
                Toast.makeText(this,
                        "Permission successfully granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        "App needs to save video to run", Toast.LENGTH_SHORT).show();
            }
        }
    }
*/
    class Wrap implements Runnable {
        @Override
        public void run() {
            try {
                main.sendTelemetry((byte) 4, isRecording ? true : false);
            } catch (Exception e) {
                Log.d(TAG, "error: " + e);
                main.logObject.saveComment("error: " + e.toString());
            }
        }
    }
}
