package com.example.nearbyfiletransfer;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.nearby.connection.Payload;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class FileUtils {
    private Context context;
    public FileUtils(Context context){
        this.context = context;
    }

    public Payload uri2Payload(Uri uri){
        try{
            ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r");
            return Payload.fromFile(pfd);
        }catch (FileNotFoundException e) {
            return null;
        }
    }

    public ArrayList<String> getFileNameFromURI(Uri uri){
        ContentResolver resolver = context.getContentResolver();
        ArrayList<String> list = new ArrayList<>();

        /**
         * query options:
         * 1.Display name of a document, used as the primary title displayed to a user.
         * 2.Concrete MIME type of a document
         **/
        String[] queryArrays = new String[]{
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };

        String fileName = null;
        String fileExtension = null;

        Cursor cursor = resolver.query(uri, queryArrays, null, null, null);
        if(cursor != null){

            int nameIndex = 0;
            int extensionIndex = 0;

            while(cursor.moveToNext()){
                nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                fileName = cursor.getString(nameIndex);
                list.add(fileName);

                extensionIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
                fileExtension = cursor.getString(extensionIndex);
                list.add(fileExtension);
            }

        }
        cursor.close();

        if(list.size() ==2 & list.get(0) != null & list.get(1) != null){
            return list;
        }
        else{
            return null;
        }
    }


    //for receiver
    /** Copies a stream from one location to another. */
    public static void copyStream(InputStream in, OutputStream out) throws IOException {
        try {
            Log.d("TAG","copying");
            //todo testing buffer size
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            Log.d("TAG","buffer statement OK");
            out.flush();
        } finally {
            in.close();
            out.close();
            Log.d("TAG", "closing");
        }
    }
}
