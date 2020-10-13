package org.androidtown.sharepic.BTPhotoTransfer;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import org.androidtown.sharepic.Album;
import org.androidtown.sharepic.AlbumDBHandler;
import org.androidtown.sharepic.MyApplication;
import org.androidtown.sharepic.Photo;
import org.androidtown.sharepic.btxfr.Client;
import org.androidtown.sharepic.btxfr.MessageType;
import org.androidtown.sharepic.btxfr.ProgressData;
import org.androidtown.sharepic.btxfr.ServerThread;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Set;

public class TransferService extends Service {
    private static final String TAG = "BTPHOTO/TransferService";
    private BluetoothAdapter adapter;
    private Handler clientHandler;
    private Handler serverHandler;
    private Client client;
    private ServerThread serverThread;
    private static final String IMAGE_FILE_NAME = "nr";
    private static final int IMAGE_QUALITY = 100;
    private ContentResolver resolver;
    private ContentObserver observer;
    private SharedPreferences preferences;
    private ArrayList<Uri> uris;

    ///////////// 앨범 추가 기능 /////////////
    private ContentResolver contentResolver;

    public TransferService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        init2();
    }

    public void init2() {
        contentResolver = this.getContentResolver();

        clientHandler = new Handler() {

            @Override
            public void handleMessage(Message message) { //보내는 쪽
                switch (message.what) {
                    case MessageType.READY_FOR_DATA: {
                        try {
                            File file = new File((String)message.obj);

                            //전처리 & 전송
                            if(file==null)
                                break;
                            /*String sendStringPath =  Environment.getExternalStorageDirectory().toString();
                            fileName = "btimage.jpg"; //임의 지정
                            File file = new File(sendStringPath,fileName);*/

                            Log.d(TAG, "ready for data");
                            ByteArrayOutputStream compressedImageStream = new ByteArrayOutputStream();
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inSampleSize = 2;
                            Bitmap image = BitmapFactory.decodeFile(file.getAbsolutePath(), options);

                            image.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, compressedImageStream);
                            byte[] compressedImage = compressedImageStream.toByteArray();
                            Log.v(TAG, "Compressed image size: " + compressedImage.length);

                            // Invoke client thread to send
                            Message imageMessage = new Message();
                            imageMessage.obj = compressedImage;
                            client.incomingHandler.sendMessage(imageMessage);

                        } catch (Exception e) {
                            Log.d(TAG, e.toString());
                            Message msg = new Message();
                            msg.obj = null;
                            client.incomingHandler.sendMessage(msg);
                        }

                        break;
                    }

                    case MessageType.COULD_NOT_CONNECT: {//전송 불가능한 상태
                        Toast.makeText(getApplicationContext(), "Could not connect to the paired device", Toast.LENGTH_SHORT).show();
                        break;
                    }

                    case MessageType.SENDING_DATA:{
                        break;
                    }

                    case MessageType.DATA_SENT_OK: {
                        Toast.makeText(getApplicationContext(), "Photo was sent successfully", Toast.LENGTH_SHORT).show();
                        break;
                    }

                    case MessageType.DIGEST_DID_NOT_MATCH: {
                        Toast.makeText(getApplicationContext(), "Photo was sent, but didn't go through correctly", Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
            }
        };


        serverHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MessageType.DATA_RECEIVED: {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = 2;
                        Bitmap image = BitmapFactory.decodeByteArray(((byte[]) message.obj), 0, ((byte[]) message.obj).length, options);
                        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
                        Date currentTime_1 = new Date();
                        String dateString = formatter.format(currentTime_1);
                        saveBitmaptoJpeg(image, "nirang", IMAGE_FILE_NAME + dateString);//파일명예시: nr20180606043124.jpg

                        break;
                    }

                    case MessageType.DIGEST_DID_NOT_MATCH: {
                        Toast.makeText(getApplicationContext(), "Photo was received, but didn't come through correctly", Toast.LENGTH_SHORT).show();
                        break;
                    }

                    case MessageType.DATA_PROGRESS_UPDATE: { //todo:없애기(우선확인위해남겨둠)
                        // some kind of update
//                        BTService.progressData = (ProgressData) message.obj;
//                        double pctRemaining = 100 - (((double) BTService.progressData.remainingSize / BTService.progressData.totalSize) * 100);
//                        if (progressDialog == null) {
//                            progressDialog = new ProgressDialog(BTStartActivity.this);
//                            progressDialog.setMessage("Receiving photo...");
//                            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
//                            progressDialog.setProgress(0);
//                            progressDialog.setMax(100);
//                            progressDialog.show();
//                        }
//                        progressDialog.setProgress((int) Math.floor(pctRemaining));
                        break;
                    }

                    case MessageType.INVALID_HEADER: {
                        Toast.makeText(getApplicationContext(), "Photo was sent, but the header was formatted incorrectly", Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
            }
        };

        adapter = BluetoothAdapter.getDefaultAdapter();

        if (serverThread == null) {
            Log.v(TAG, "Starting server thread.  Able to accept photos.");
            serverThread = new ServerThread(adapter, serverHandler);
            serverThread.start();
        }

//        Log.v(TAG, "Starting client thread");
//        if (clientThread != null) {
//            clientThread.cancel();
//        }
//        clientThread = new ClientThread(device, clientHandler);
//        clientThread.start();

        resolver = getContentResolver();
        observer = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange((selfChange));
                Log.i("DB변경", "DB가 변경됨을 감지했습니다.");
                ArrayList<String> filePaths = getAddedFile();
                if(filePaths.size() > 0) {
                    moveFile(filePaths);
                }
            }
        };

        resolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong("lastDatabaseUpdateTime", (new Date()).getTime()); //현재 시간 찍는다.
        editor.commit();
    }

    public void saveBitmaptoJpeg(Bitmap bitmap, String folder, String name){ //bitmap객체를 jpg파일로 변환해 저장.

        String ex_storage = Environment.getExternalStorageDirectory().toString();
        // Get Absolute Path in External Sdcard

        String foler_name = "/"+folder+"/";
        String file_name = name+".jpg"; //=fname
        String string_path = ex_storage+foler_name;

        File file_path;

        try{
            file_path = new File(string_path); // file
            if(!file_path.isDirectory()) {
                file_path.mkdirs();
            }

            //기연 수정. file 정보 수정
            File nirang_file = new File(file_path, file_name);
            FileOutputStream out = new FileOutputStream(nirang_file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);

            ///////////// 앨범 추가 기능 /////////////
            insertAlbum(nirang_file);

            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE); //미디어 스캐닝 (갤러리에 앨범 띄워주기 위해)
            intent.setData(Uri.fromFile(nirang_file));
            sendBroadcast(intent);
            out.flush();
            out.close();
        }catch(FileNotFoundException exception){
            Log.e("FileNotFoundException", exception.getMessage());
        }catch(IOException exception){
            Log.e("IOException", exception.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    void moveFile(ArrayList<String> filePaths) {
        Log.d(TAG, "*moveFile*");

        String filePath;
        Uri uri;
        for(int i=0;i<filePaths.size();i++){
            filePath = filePaths.get(i);
            Log.d(TAG, "file: " + filePath);

            uri = uris.get(i);
            File file = new File(filePath);

            ///////////// 앨범 추가 /////////////
            insertAlbum(file);

            //자동전송 기연추가
            for (BluetoothDevice device : adapter.getBondedDevices()) {
                if (((MyApplication)getApplication()).getDevice().equals(device)) { //서비스에서 받아오도록했음
                    if(client == null){
                        client = new Client(device, clientHandler);
                    }

                    client.setmFilePath(filePath);
                    new Thread(client).start();
                }
            }
        }
    }

    ArrayList<String> getAddedFile(){
        Log.d(TAG, "*getAddedFile*");

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        long latestAddedTime = preferences.getLong("latestAddedTime", 0); //전에 추가됐던 시간 가져옴

        String[] projection = { MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_ADDED };

        String where = MediaStore.MediaColumns.DATE_ADDED + ">" + (latestAddedTime); //조건 : 전에 추가됐던 시간 이후에 추가 된 사진

        Cursor imageCursor = MediaStore.Images.Media.query(getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, where, null, null);


        ArrayList<String> filePaths = new ArrayList<>();
        uris = new ArrayList<>(imageCursor.getCount());

        int dataColumnIndex = imageCursor.getColumnIndex(projection[0]);
        int dateAddedColumnIndex = imageCursor.getColumnIndex(projection[1]);

        long latestAddedTimeTmp = latestAddedTime;
        if (imageCursor != null){
            int count = imageCursor.getCount();
            if (count > 0 && imageCursor.moveToFirst()) {
                do {
                    String filePath = imageCursor.getString(dataColumnIndex);
                    Log.d(TAG,"added File Path: " + filePath);
                    if(!filePath.contains("nirang")) {
                        long addedTime = Long.parseLong(imageCursor.getString(dateAddedColumnIndex));
                        if(addedTime > latestAddedTime){
                            latestAddedTime = addedTime;
                        }

                        filePaths.add(filePath);
                        Uri imageUri = Uri.parse(filePath);
                        uris.add(imageUri);
                    }
                } while(imageCursor.moveToNext());

                if(latestAddedTimeTmp != latestAddedTime) {
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putLong("latestAddedTime", latestAddedTime); //새로 찍는다.
                    editor.commit();
                }
            }
        }

        Log.d(TAG, "added file count: " + filePaths.size());
        imageCursor.close();

        return filePaths;
    }

    ///////////// 앨범 추가 기능 /////////////
    private void insertAlbum(File file1) {
        String bUri = getImageContentUri(this, file1).toString();
        System.out.println(bUri);
        String imageId = bUri.substring(bUri.lastIndexOf("/")+1);
        System.out.println(imageId);
        Uri thumbnailUri = uriToThumbnail(imageId);
        Photo photo = new Photo(thumbnailUri, Uri.parse(file1.getAbsolutePath()));
        MyApplication myapp = (MyApplication)getApplication();
        if (myapp.getAlbum_title() != null) {
            System.out.println(myapp.getAlbum_title());
            newProduct(photo);
        }
    }

    public static Uri getImageContentUri(Context context, File imageFile) {
        String filePath = imageFile.getAbsolutePath();
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[] { MediaStore.Images.Media._ID },
                MediaStore.Images.Media.DATA + "=? ",
                new String[] { filePath }, null);
        if (cursor != null && cursor.moveToFirst()) {
            int id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID));
            cursor.close();
            return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "" + id);
        } else {
            if (imageFile.exists()) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DATA, filePath);
                return context.getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            } else {
                return null;
            }
        }
    }

    private Uri uriToThumbnail(String imageId) {
        String[] projection = {MediaStore.Images.Thumbnails.DATA};

        Cursor thumbnailCursor = contentResolver.query(
                MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI,
                projection, // Which columns to return
                MediaStore.Images.Thumbnails.IMAGE_ID + "=?",
                new String[]{imageId},
                null);

        if (thumbnailCursor.moveToFirst()) {
            int thumbnailColumnIndex = thumbnailCursor.getColumnIndex(projection[0]);
            // Generate a tiny thumbnail version.
            String thumbnailPath = thumbnailCursor.getString(thumbnailColumnIndex);
            thumbnailCursor.close();
            return Uri.parse(thumbnailPath);
        } else {
            MediaStore.Images.Thumbnails.getThumbnail(contentResolver, Long.parseLong(imageId), MediaStore.Images.Thumbnails.MINI_KIND, null);
            thumbnailCursor.close();
            return uriToThumbnail(imageId);
        }
    }

    public void newProduct(Photo photo) { // insert
        AlbumDBHandler dbHandler = new AlbumDBHandler(this, null, null, 1);
        MyApplication myapp = (MyApplication)getApplication();
        Album product = new Album(myapp.getAlbum_title(), photo);
        dbHandler.addProduct(product);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onDestroy() {
        client = null;
        serverThread = null;
        super.onDestroy();
    }
}
