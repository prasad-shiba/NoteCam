package com.demo.notecam;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.demo.notecam.databinding.ActivityCameraBinding;
import com.demo.notecam.databinding.MetaDataBinding;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {

  private static final String TAG = "CameraActivity";
  private static final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";
  private static final int REQUEST_PERMISSION_SETTING = 12;
  private final LocationUtil locationUtil = new LocationUtil(this);
  private ActivityCameraBinding binding;
  private MetaDataBinding metaDataBinding;
  private Location location;
  private final LocationUtil.LocationResult locationResult =
      new LocationUtil.LocationResult() {
        @Override
        public void gotLocation(Location location) {
          // Got the location!
          CameraActivity.this.location = location;
          if (metaDataBinding != null && location != null) {
            metaDataBinding.longitudeTv.setVisibility(View.VISIBLE);
            metaDataBinding.latitudeTv.setVisibility(View.VISIBLE);
            metaDataBinding.accuracyTv.setVisibility(View.VISIBLE);
            metaDataBinding.accuracyTv.setText(
                getString(R.string.accuracy) + " " + location.getAccuracy() + " m");
            metaDataBinding.latitudeTv.setText(
                getString(R.string.latitude) + " " + location.getLatitude());
            metaDataBinding.longitudeTv.setText(
                getString(R.string.longitude) + " " + location.getLongitude());
          }
        }
      };
  private SoundPool soundPool = new SoundPool.Builder().build();
  private int soundId;
  private ImageCapture imageCapture;
  private ActivityResultLauncher<String[]> resultLauncher;
  private ExecutorService cameraExecutor;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setFullScreen();

    binding = ActivityCameraBinding.inflate(getLayoutInflater());
    metaDataBinding = binding.metaData;
    setContentView(binding.getRoot());

    // Request camera permissions
    init();
    if (Util.isPermissionGranted(this, Manifest.permission.CAMERA)) {
      startCamera();
    } else {
      requestPermission(Manifest.permission.CAMERA);
    }

    locationUtil.getLocation(locationResult);
    init();
    metaDataBinding.timeTv.setText(getString(R.string.time) + " " + Util.getCurrentTime());
    metaDataBinding.noteTv.setText(
        getString(R.string.note) + " " + Build.MODEL + "(" + Build.MANUFACTURER + ")");
    metaDataBinding.longitudeTv.setVisibility(View.GONE);
    metaDataBinding.latitudeTv.setVisibility(View.GONE);
    metaDataBinding.accuracyTv.setVisibility(View.GONE);
    // Set up the listeners for take photo capture and other buttons
    onClickEvents();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    cameraExecutor.shutdown();
    soundPool.release();
    soundPool = null;
  }

  //
  // ---------------------------------------------------------------------------------------------------

  private Dialog getDialog(String permission) {
    return Util.createPermissionDialog(
        this,
        permission,
        new OnPermissionDialogButtonClickListener() {
          @Override
          public void onPositiveButtonClicked(MaterialButton button) {
            if (button.getText().equals(getString(R.string.settings))) {
              openAppSettings();
            } else {
              resultLauncher.launch(new String[] {permission});
            }
          }

          @Override
          public void onNegativeButtonClicked(MaterialButton button) {
            if (button.getText().equals(getString(R.string.exit))) {
              finish();
            }
          }
        });
  }

  private void init() {
    cameraExecutor = Executors.newSingleThreadExecutor();
    soundId = soundPool.load(this, R.raw.photo_click, 1);
    resultLauncher =
        registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
              Boolean cameraGranted = result.getOrDefault(Manifest.permission.CAMERA, false);
              Boolean fineLocationGranted =
                  result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
              Boolean coarseLocationGranted =
                  result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
              if (cameraGranted != null && cameraGranted) {
                // camera access granted
                startCamera();
              } else {
                getDialog(Manifest.permission.CAMERA).show();
              }

              if (fineLocationGranted != null && fineLocationGranted) {
                // Precise location access granted.
              } else if (coarseLocationGranted != null && coarseLocationGranted) {
                // Only approximate location access granted.
              } else {
                // No location access granted.
                createPermissionSnackBar();
              }
            });
  }

  private void onClickEvents() {
    binding.capture.setOnClickListener(view -> takePhoto());
    binding.photos.setOnClickListener(v -> Util.showShortToast(this, "photos clicked"));
    binding.settings.setOnClickListener(v -> Util.showShortToast(this, "Settings clicked"));
    binding.switchCamera.setOnClickListener(v -> switchCamera());
    binding.flashImage.setOnClickListener(v -> Util.showShortToast(this, "flash clicked"));
  }

  public void openAppSettings() {
    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
    intent.setData(Uri.parse("package:" + getPackageName()));
    startActivityForResult(intent, REQUEST_PERMISSION_SETTING);
  }

  private void requestPermission(String permission) {
    resultLauncher.launch(new String[] {permission});
  }

  private void requestPermissions(String[] permissions) {
    // Before you perform the actual permission request, check whether your app
    // already has the permissions, and whether your app needs to show a permission
    // rationale dialog. For more details, see Request permissions.
    resultLauncher.launch(permissions);
  }

  private void setFullScreen() {
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow()
        .setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
  }

  private void startCamera() {
    if (!Util.isPermissionGranted(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        && !Util.isPermissionGranted(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
      requestPermissions(
          new String[] {
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION
          });
    }
    ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
        ProcessCameraProvider.getInstance(this);
    cameraProviderFuture.addListener(
        () -> {
          // Used to bind the lifecycle of cameras to the lifecycle owner
          ProcessCameraProvider cameraProvider = null;
          try {
            cameraProvider = cameraProviderFuture.get();
          } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
          }

          // Preview
          Preview preview = new Preview.Builder().build();
          preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider());

          imageCapture = new ImageCapture.Builder().build();

          // Select back camera as a default
          CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

          try {
            // Unbind use cases before rebinding
            if (cameraProvider != null) {
              cameraProvider.unbindAll();
              // Bind use cases to camera
              cameraProvider.bindToLifecycle(
                  CameraActivity.this, cameraSelector, preview, imageCapture);
            }
          } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
          }
        },
        ContextCompat.getMainExecutor(this));
  }

  private void switchCamera() {
    Util.showShortToast(this, "Switch Camera, Not yet Implemented!");
  }

  private void takePhoto() {
    // Get a stable reference of the modifiable image capture use case
    ImageCapture imageCapture = this.imageCapture;
    if (imageCapture == null) {
      return;
    }

    // Create output options object which contains file + metadata
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(this),
        new ImageCapture.OnImageCapturedCallback() {
          @Override
          public void onCaptureSuccess(@NonNull ImageProxy image) {
            super.onCaptureSuccess(image);
            ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[byteBuffer.capacity()];
            byteBuffer.get(bytes);
            soundPool.play(soundId, 1, 1, 0, 0, 1);
            Bitmap res;
            if (location != null) {
              res =
                  Util.addWaterMark(
                      BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null),
                      true,
                      location.getLongitude(),
                      location.getLatitude(),
                      location.getAccuracy());
            } else {
              res =
                  Util.addWaterMark(
                      BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null),
                      false,
                      -1,
                      -1,
                      -1);
            }
            Util.saveImage(CameraActivity.this, res);
          }
        });
  }

  private void createPermissionSnackBar() {
    Snackbar.make(binding.getRoot(), R.string.location_title, Snackbar.LENGTH_LONG)
        .setAction(R.string.settings, v -> openAppSettings())
        .show();
  }
}
