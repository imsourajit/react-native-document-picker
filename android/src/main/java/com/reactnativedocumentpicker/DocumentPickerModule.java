package com.reactnativedocumentpicker;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.GuardedResultAsyncTask;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Date;

import android.provider.MediaStore;



@ReactModule(name = DocumentPickerModule.NAME)
public class DocumentPickerModule extends ReactContextBaseJavaModule {

  public static final String NAME = "RNDocumentPicker";
  private static final int READ_REQUEST_CODE = 41;
  private static final int PICK_DIR_REQUEST_CODE = 42;

  private static final String E_ACTIVITY_DOES_NOT_EXIST = "ACTIVITY_DOES_NOT_EXIST";
  private static final String E_FAILED_TO_SHOW_PICKER = "FAILED_TO_SHOW_PICKER";
  private static final String E_DOCUMENT_PICKER_CANCELED = "DOCUMENT_PICKER_CANCELED";
  private static final String E_UNABLE_TO_OPEN_FILE_TYPE = "UNABLE_TO_OPEN_FILE_TYPE";
  private static final String E_UNKNOWN_ACTIVITY_RESULT = "UNKNOWN_ACTIVITY_RESULT";
  private static final String E_INVALID_DATA_RETURNED = "INVALID_DATA_RETURNED";
  private static final String E_UNEXPECTED_EXCEPTION = "UNEXPECTED_EXCEPTION";

  private static final String OPTION_TYPE = "type";
  private static final String OPTION_MULTIPLE = "allowMultiSelection";
  private static final String OPTION_COPY_TO = "copyTo";

  private static final String FIELD_URI = "uri";
  private static final String FIELD_FILE_COPY_URI = "fileCopyUri";
  private static final String FIELD_COPY_ERROR = "copyError";
  private static final String FIELD_NAME = "name";
  private static final String FIELD_TYPE = "type";
  private static final String FIELD_SIZE = "size";
  private static final String FIELD_CREATION = "creationTime";

  private static String temp = "";

  private final ActivityEventListener activityEventListener = new BaseActivityEventListener() {
    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
      final Promise storedPromise = promise;
      if (storedPromise == null) {
        Log.e(NAME, "promise was null in onActivityResult");
        return;
      }
      if (requestCode == READ_REQUEST_CODE) {
        onShowActivityResult(resultCode, data, storedPromise);
      } else if (requestCode == PICK_DIR_REQUEST_CODE) {
        onPickDirectoryResult(resultCode, data);
      }
    }
  };

  private String[] readableArrayToStringArray(ReadableArray readableArray) {
    int size = readableArray.size();
    String[] array = new String[size];
    for (int i = 0; i < size; ++i) {
      array[i] = readableArray.getString(i);
    }
    return array;
  }

  private Promise promise;
  private String copyTo;

  public DocumentPickerModule(ReactApplicationContext reactContext) {
    super(reactContext);
    reactContext.addActivityEventListener(activityEventListener);
  }

  @Override
  public void onCatalystInstanceDestroy() {
    super.onCatalystInstanceDestroy();
    getReactApplicationContext().removeActivityEventListener(activityEventListener);
  }

  @NonNull
  @Override
  public String getName() {
    return NAME;
  }

  @ReactMethod
  public void pick(ReadableMap args, Promise promise) {
    Activity currentActivity = getCurrentActivity();
    this.promise = promise;
    this.copyTo = args.hasKey(OPTION_COPY_TO) ? args.getString(OPTION_COPY_TO) : null;

    if (currentActivity == null) {
      sendError(E_ACTIVITY_DOES_NOT_EXIST, "Current activity does not exist");
      return;
    }

    try {
      Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
      intent.addCategory(Intent.CATEGORY_OPENABLE);
      intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);


      intent.setType("*/*");
      if (!args.isNull(OPTION_TYPE)) {
        ReadableArray types = args.getArray(OPTION_TYPE);
        if (types != null) {
          if (types.size() > 1) {
            String[] mimeTypes = readableArrayToStringArray(types);
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            intent.setType(String.join("|",mimeTypes));
          } else if (types.size() == 1) {
            intent.setType(types.getString(0));
          }
        }
      }

      boolean multiple = !args.isNull(OPTION_MULTIPLE) && args.getBoolean(OPTION_MULTIPLE);
      intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);

      currentActivity.startActivityForResult(intent, READ_REQUEST_CODE, Bundle.EMPTY);
    } catch (ActivityNotFoundException e) {
      sendError(E_UNABLE_TO_OPEN_FILE_TYPE, e.getLocalizedMessage());
    } catch (Exception e) {
      e.printStackTrace();
      sendError(E_FAILED_TO_SHOW_PICKER, e.getLocalizedMessage());
    }
  }

  @ReactMethod
  public void pickDirectory(Promise promise) {
    Activity currentActivity = getCurrentActivity();

    if (currentActivity == null) {
      promise.reject(E_ACTIVITY_DOES_NOT_EXIST, "Current activity does not exist");
      return;
    }
    this.promise = promise;
    try {
      Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
      currentActivity.startActivityForResult(intent, PICK_DIR_REQUEST_CODE, null);
    } catch (Exception e) {
      sendError(E_FAILED_TO_SHOW_PICKER, "Failed to create directory picker", e);
    }
  }


  @ReactMethod
  public void deleteFile(String fileUri) {
    Uri fileContent = Uri.parse(fileUri);
  DocumentFile.fromSingleUri(getReactApplicationContext().getApplicationContext(), fileContent).delete();
  }

  @ReactMethod
  public void deleteCache() {
    try {
      File dir = getReactApplicationContext().getApplicationContext().getCacheDir();
      deleteDir(dir);
    } catch (Exception e) { e.printStackTrace();}
  }

  public static boolean deleteDir(File dir) {
    if (dir != null && dir.isDirectory()) {
      String[] children = dir.list();
      for (int i = 0; i < children.length; i++) {
        boolean success = deleteDir(new File(dir, children[i]));
        if (!success) {
          return false;
        }
      }
      return dir.delete();
    } else if(dir!= null && dir.isFile()) {
      return dir.delete();
    } else {
      return false;
    }
  }

  private void onPickDirectoryResult(int resultCode, Intent data) {
    if (resultCode == Activity.RESULT_CANCELED) {
      sendError(E_DOCUMENT_PICKER_CANCELED, "User canceled directory picker");
      return;
    } else if (resultCode != Activity.RESULT_OK) {
      sendError(E_UNKNOWN_ACTIVITY_RESULT, "Unknown activity result: " + resultCode);
      return;
    }

    if (data == null || data.getData() == null) {
      sendError(E_INVALID_DATA_RETURNED, "Invalid data returned by intent");
      return;
    }
    Uri uri = data.getData();

    WritableMap map = Arguments.createMap();
    map.putString(FIELD_URI, uri.toString());
    promise.resolve(map);
  }

  public void onShowActivityResult(int resultCode, Intent data, Promise promise) {
    if (resultCode == Activity.RESULT_CANCELED) {
      sendError(E_DOCUMENT_PICKER_CANCELED, "User canceled document picker");
    } else if (resultCode == Activity.RESULT_OK) {
      Uri uri = null;
      ClipData clipData = null;

      if (data != null) {
        uri = data.getData();
        clipData = data.getClipData();
      }

      try {
        List<Uri> uris = new ArrayList<>();
        // condition order seems to matter: https://github.com/rnmods/react-native-document-picker/issues/317#issuecomment-645222635
        if (clipData != null && clipData.getItemCount() > 0) {
          final int length = clipData.getItemCount();
          for (int i = 0; i < length; ++i) {
            ClipData.Item item = clipData.getItemAt(i);
            uris.add(item.getUri());
          }
        } else if (uri != null) {
          uris.add(uri);
        } else {
          sendError(E_INVALID_DATA_RETURNED, "Invalid data returned by intent");
          return;
        }

        new ProcessDataTask(getReactApplicationContext(), uris, copyTo, promise).execute();
      } catch (Exception e) {
        sendError(E_UNEXPECTED_EXCEPTION, e.getLocalizedMessage(), e);
      }
    } else {
      sendError(E_UNKNOWN_ACTIVITY_RESULT, "Unknown activity result: " + resultCode);
    }
  }

  private static class ProcessDataTask extends GuardedResultAsyncTask<ReadableArray> {
    private final WeakReference<Context> weakContext;
    private final List<Uri> uris;
    private final String copyTo;
    private final Promise promise;

    protected ProcessDataTask(ReactContext reactContext, List<Uri> uris, String copyTo, Promise promise) {
      super(reactContext.getExceptionHandler());
      this.weakContext = new WeakReference<>(reactContext.getApplicationContext());
      this.uris = uris;
      this.copyTo = copyTo;
      this.promise = promise;
    }

    @Override
    protected ReadableArray doInBackgroundGuarded() {
      WritableArray results = Arguments.createArray();
      for (Uri uri : uris) {
        results.pushMap(getMetadata(uri));
      }
      return results;
    }

    @Override
    protected void onPostExecuteGuarded(ReadableArray readableArray) {
      promise.resolve(readableArray);
    }

    private WritableMap getMetadata(Uri uri) {
      Context context = weakContext.get();
      if (context == null) {
        return Arguments.createMap();
      }
      ContentResolver contentResolver = context.getContentResolver();
      WritableMap map = Arguments.createMap();
      map.putString(FIELD_URI, uri.toString());

      if(uri.toString().contains("content")) {
        temp = getCreationTime(context, uri);
        map.putString(FIELD_CREATION, temp );
      } else {
        map.putNull(FIELD_CREATION);
      }

      map.putString(FIELD_TYPE, contentResolver.getType(uri));
      try (Cursor cursor = contentResolver.query(uri, null, null, null, null, null)) {
        if (cursor != null && cursor.moveToFirst()) {
          int displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
          if (!cursor.isNull(displayNameIndex)) {
            String fileName = cursor.getString(displayNameIndex);
            map.putString(FIELD_NAME, fileName);
          } else {
            map.putNull(FIELD_NAME);
          }
          int mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
          if (!cursor.isNull(mimeIndex)) {
            map.putString(FIELD_TYPE, cursor.getString(mimeIndex));
          }
          int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
          if (cursor.isNull(sizeIndex)) {
            map.putNull(FIELD_SIZE);
          } else {
            map.putDouble(FIELD_SIZE, cursor.getLong(sizeIndex));
          }
        }
      }

      prepareFileUri(context, map, uri);
      return map;
    }


    private static String getCreationTime(Context context, Uri uri) {
      DocumentFile document =  DocumentFile.fromSingleUri(context, uri);
      System.out.println("__Value is = document file "+document.lastModified());
//      String[] projection = {MediaStore.Video.Media.DATE_MODIFIED};
//      ContentResolver contentResolver = context.getContentResolver();
//      Cursor cursor = contentResolver.query(uri, projection, null, null, null);
//      if (cursor != null && cursor.moveToFirst()) {
//        long creationTime = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED));
//        System.out.println("__Value is = "+cursor);
//
//        cursor.close();
//        // use lastModified timestamp
//        return "";
//      }
      return Long.toString(document.lastModified());
    }

    private void prepareFileUri(Context context, WritableMap map, Uri uri) {
      if (copyTo == null) {
        map.putNull(FIELD_FILE_COPY_URI);
      } else {
        copyFileToLocalStorage(context, map, uri);
      }
    }

    private void copyFileToLocalStorage(Context context, WritableMap map, Uri uri) {
      File dir = context.getCacheDir();
      if (copyTo.equals("documentDirectory")) {
        dir = context.getFilesDir();
      }
      // we don't want to rename the file so we put it into a unique location
      dir = new File(dir, UUID.randomUUID().toString());
      try {
        boolean didCreateDir = dir.mkdir();
        if (!didCreateDir) {
          throw new IOException("failed to create directory at " + dir.getAbsolutePath());
        }
        String fileName = map.getString(FIELD_NAME);
        if (fileName == null) {
          fileName = String.valueOf(System.currentTimeMillis());
        }
        File destFile = new File(dir, fileName);
        Uri copyPath = copyFile(context, uri, destFile);
        map.putString(FIELD_FILE_COPY_URI, copyPath.toString());
      } catch (Exception e) {
        e.printStackTrace();
        map.putNull(FIELD_FILE_COPY_URI);
        map.putString(FIELD_COPY_ERROR, e.getLocalizedMessage());
      }
    }

    public static Uri copyFile(Context context, Uri uri, File destFile) throws IOException {
      try(InputStream inputStream = context.getContentResolver().openInputStream(uri);
          FileOutputStream outputStream = new FileOutputStream(destFile)) {
        byte[] buf = new byte[8192];
        int len;
        while ((len = inputStream.read(buf)) > 0) {
          outputStream.write(buf, 0, len);
        }
        return Uri.fromFile(destFile);
      }
    }
  }

  private void sendError(String code, String message) {
    sendError(code, message, null);
  }

  private void sendError(String code, String message, Exception e) {
    Promise temp = this.promise;
    if (temp != null) {
      this.promise = null;
      temp.reject(code, message, e);
    }
  }
}
