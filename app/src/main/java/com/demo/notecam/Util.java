package com.demo.notecam;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.icu.text.SimpleDateFormat;
import android.icu.util.Calendar;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

public final class Util {
  private static final String DIRECTORY_NAME = "My Note Camera";

  private Util() {}

  public static Bitmap addWaterMark(
      Bitmap src, boolean isLocation, double longitude, double latitude, float accuracy) {
    int w = src.getWidth();
    int h = src.getHeight();
    Bitmap result = Bitmap.createBitmap(w, h, src.getConfig());

    Canvas canvas = new Canvas(result);
    canvas.drawBitmap(src, 0, 0, null);

    // for white rectangular draw
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(Color.WHITE);
    if (isLocation) {
      canvas.drawRect(0, h - 550, w / 3, h, paint);
    } else {
      canvas.drawRect(0, h - 250, w / 3, h, paint);
    }

    // for text draw
    TextPaint textPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    textPaint.setColor(Color.BLACK);
    textPaint.setTextSize(80);
    textPaint.setWordSpacing(0.1F);

    canvas.drawText("Time : " + getCurrentTime(), 50, h - 150, textPaint);
    canvas.drawText(
        "Note : " + Build.MODEL + "(" + Build.MANUFACTURER + ")", 50, h - 50, textPaint);

    if (isLocation) {
      canvas.drawText("Latitude : ", 50, h - 450, textPaint);
      canvas.drawText("Longitude : ", 50, h - 350, textPaint);
      canvas.drawText("Accuracy : ", 50, h - 250, textPaint);
    }

    return result;
  }

  public static boolean isPermissionGranted(Context context, String permission) {
    return ContextCompat.checkSelfPermission(context, permission)
        == PackageManager.PERMISSION_GRANTED;
  }

  public static void showShortToast(Activity activity, String msg) {
    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
  }

  public static boolean isLocationPermissionGranted(Context context) {
    return isPermissionGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION);
  }

  public static boolean isWriteExternalStoragePermissionGranted(Context context) {
    if (Build.VERSION_CODES.P >= Build.VERSION.SDK_INT) {
      return isPermissionGranted(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    } else {
      throw new RuntimeException("Max SDK version 28");
    }
  }

  public static boolean isCameraPermissionGranted(Context context) {
    return isPermissionGranted(context, Manifest.permission.CAMERA);
  }

  public static Dialog createPermissionDialog(
      CameraActivity activity, String permission, OnPermissionDialogButtonClickListener listener) {
    boolean isFinishWhenBackPressed = false;
    Dialog dialog = new Dialog(activity);
    dialog.setContentView(R.layout.permission_dialog);
    dialog
        .getWindow()
        .setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

    MaterialButton posBtn = dialog.findViewById(R.id.pos_btn);
    MaterialButton negBtn = dialog.findViewById(R.id.neg_btn);
    MaterialTextView descTv = dialog.findViewById(R.id.message_tv);
    MaterialTextView titleTv = dialog.findViewById(R.id.title_tv);

    if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
      Util.showShortToast(activity, "show");
    } else {
      Util.showShortToast(activity, "don't show");
    }

    if (Manifest.permission.CAMERA.equals(permission)) {
      negBtn.setText(R.string.exit);
      titleTv.setText(R.string.camera_title);
      isFinishWhenBackPressed = true;
      if (activity.shouldShowRequestPermissionRationale(permission)) {
        posBtn.setText(R.string.allow);
        descTv.setText(R.string.camera_description);
      } else {
        posBtn.setText(R.string.settings);
        descTv.setText(R.string.camera_description_after_deny);
      }
    } else if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)) {
      negBtn.setText(R.string.deny);
      titleTv.setText(R.string.storage_title);
      if (activity.shouldShowRequestPermissionRationale(permission)) {
        posBtn.setText(R.string.allow);
        descTv.setText(R.string.storage_description);
      } else {
        posBtn.setText(R.string.settings);
        descTv.setText(R.string.storage_description_after_deny);
      }
    } else {
      negBtn.setText(R.string.deny);
      titleTv.setText(R.string.location_title);
      if (activity.shouldShowRequestPermissionRationale(permission)) {
        posBtn.setText(R.string.allow);
        descTv.setText(R.string.location_description);
      } else {
        posBtn.setText(R.string.settings);
        descTv.setText(R.string.location_description_after_deny);
      }
    }

    posBtn.setOnClickListener(
        v -> {
          listener.onPositiveButtonClicked(posBtn);
          dialog.dismiss();
        });
    negBtn.setOnClickListener(
        v -> {
          listener.onNegativeButtonClicked(negBtn);
          dialog.dismiss();
        });

    dialog.setCancelable(false);
    // dismiss dialog when back key pressed
    boolean finalIsFinishWhenBackPressed = isFinishWhenBackPressed;
    dialog.setOnKeyListener(
        (dialog1, keyCode, event) -> {
          if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            dialog.dismiss();
            if (finalIsFinishWhenBackPressed) {
              activity.finish();
            }
          }
          return true;
        });

    return dialog;
  }

  public static String getCurrentTime() {
    return new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
        .format(Calendar.getInstance());
  }

  public static void saveImage(Context context, Bitmap bitmap) {
    // Create time stamped name and MediaStore entry.
    final ContentValues contentValues = new ContentValues();
    contentValues.put(
        MediaStore.MediaColumns.DISPLAY_NAME, String.valueOf(System.currentTimeMillis()));
    contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
    contentValues.put(
        MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/" + DIRECTORY_NAME);
    final ContentResolver resolver = context.getContentResolver();
    Uri uri = null;
    try {
      final Uri contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
      uri = resolver.insert(contentUri, contentValues);

      if (uri == null) throw new IOException("Failed to create new MediaStore record.");

      try (final OutputStream stream = resolver.openOutputStream(uri)) {
        if (stream == null) throw new IOException("Failed to open output stream.");

        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream))
          throw new IOException("Failed to save bitmap.");
      }
    } catch (IOException e) {

      if (uri != null) {
        // Don't leave an orphan entry in the MediaStore
        resolver.delete(uri, null, null);
      }
    }
  }

  private boolean isAllPermissionGranted(Context context) {
    boolean granted = isCameraPermissionGranted(context) && isLocationPermissionGranted(context);
    if (Build.VERSION_CODES.P >= Build.VERSION.SDK_INT) {
      granted = granted && isWriteExternalStoragePermissionGranted(context);
    }
    return granted;
  }
}
