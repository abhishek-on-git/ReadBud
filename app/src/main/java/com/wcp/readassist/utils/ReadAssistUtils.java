package com.wcp.readassist.utils;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;

import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.rendering.PDFRenderer;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.wcp.readassist.DigitalPage;
import com.wcp.readassist.MainActivity;
import com.wcp.readassist.R;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class ReadAssistUtils {

    public static final String TAG = "ReadAssistUtils";
    private static String mResultText;
    private static final List<DigitalPage> mCurrDigitalPageList = new ArrayList<>();
    private static TaskStatusCallback mCallback;
    private static String mFileName;
    private static File mOutputFile;
    private static AppUpdateManager mAppUpdateManager;
    private static int mLastImagePageIndex;

    public static final String SOURCE = "SOURCE"; // 0 for image, 1 for pdf
    public static final String BOOK_NAME = "BOOK_NAME";
    public static final String ROOT_DIR = "Read Assist";
    public static final String DB_NAME = "Dictionary.db";
    public static final String DB_PATH = "/databases/" + DB_NAME;
    public static final String DB_ROOT = ROOT_DIR + "/databases";
    public static final String MIME_TYPE_PLAIN_TEXT = "text/plain";
    private static final String PAGE_SEPARATOR = " ReadAssist ";

    public static final int APP_UPDATE_REQUEST_CODE = 1002;
    public static final int GALLERY_IMG_REQUEST_CODE = 1003;
    public static final int MAX_IMAGE_WIDTH = 720;
    public static final String CAMERA_BACKGROUND_THREAD_NAME = "camera_background_thread";
    public static final String PDF_EXTRACTION_BACKGROUND_THREAD = "pdf_background_thread";
    public static final String DICTIONARY_CREATION_BACKGROUND_THREAD = "dictionary_background_thread";
    public static final String TEXT_EXTRACTION_BACKGROUND_THREAD = "dictionary_background_thread";
    public static final String HELP_NEEDED_PREFERENCE_KEY = "show_help_preference";
    public static final String TEXTURE_SHARED_PREFERENCE_KEY = "texture_preference";
    public static final String EBOOK_STATE_PREFERENCE = "ebook_state_preference";
    public static final String INTERSTITIAL_AD_ENABLED_PREFERENCE = "ad_preference";
    public static final String INTERSTITIAL_AD_ENABLED_PREFERENCE_KEY = "ad_preference_key";
    public static final String PAGE_NUMBER_PREFERENCE_HEAD = "ReadBud_";

    public interface TaskStatusCallback {
        void onTextRecognitionCompleted(String eBookName, List<DigitalPage> digitalPageList);
        void onFileWritten(String eBookName);
        void onFileRead(String eBookName, String[] text);
        void onTextExtractionComplete(String eBookName);
        void onPageExtractionComplete(int pageNumber);
        void onError(boolean showMessage, String fileName);
        void onDBcreated();
    }

    public static void registerTaskStatusCalback(TaskStatusCallback callback) {
        mCallback = callback;
    }

    public static void copyDictionaryDB(Context context) {
        try {
            InputStream inputStream = context.getAssets().open(DB_NAME);
            File root = context.getExternalFilesDir(DB_ROOT);
            if(!root.exists()) {
                root.mkdirs();
            }
            String outputDBPath = root.toString() + "/" + DB_NAME;
            OutputStream outputStream = new FileOutputStream(outputDBPath);
            byte[] buffer = new byte[1024];
            while (inputStream.read(buffer) > 0) {
                outputStream.write(buffer);
            }
            outputStream.flush();
            outputStream.close();
            inputStream.close();
            mCallback.onDBcreated();
        } catch (Exception e) {
            Log.e(TAG, "Exception while copying DB : "+e);
        }
    }

    public static int getNavigationBarHeight(Context context) {
        Resources resources = context.getResources();
        int resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return resources.getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    public static boolean hasSoftNavigationBar(Resources resources) {
        try {
            int id = resources.getIdentifier("config_showNavigationBar", "bool", "android");
            return id > 0 && resources.getBoolean(id);
        } catch (Exception e) {
            Log.e(TAG, "Navbar exception : "+e);
            return false;
        }
    }

    public static void extractTextFromImage(Context context, List<Uri> imageUris, Handler handler) {
        List<Bitmap> imageList = new ArrayList<Bitmap>();
        extractTextFromImageList(context, imageList, handler, "Extracted text", imageUris);
    }

    private static int getInSampleSize(BitmapFactory.Options options) {
        int inSampleSize = 1;
        final int width = options.outWidth;
        if(width > MAX_IMAGE_WIDTH) {
            int halfWidth = width/2;
            while(halfWidth / inSampleSize >= MAX_IMAGE_WIDTH) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    public static Bitmap rotateImageIfRequired(Context context, Bitmap img, Uri selectedImage){
        try {
            InputStream input = context.getContentResolver().openInputStream(selectedImage);
            ExifInterface ei;
            if (Build.VERSION.SDK_INT > 23)
                ei = new ExifInterface(input);
            else
                ei = new ExifInterface(selectedImage.getPath());

            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Log.e(TAG, "orientation : "+orientation);

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return rotateImage(img, 90);
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return rotateImage(img, 180);
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return rotateImage(img, 270);
                default:
                    return img;
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception in rotating image : "+e);
            return null;
        }
    }

    private static Bitmap rotateImage(Bitmap img, int degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
        img.recycle();
        return rotatedImg;
    }

    public static Bitmap getBitmapFromURI(Context context, Uri imageUri) {
        try {
            InputStream input = context.getContentResolver().openInputStream(imageUri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, options);
            input.close();
            int inSampleSize = getInSampleSize(options);
            Log.e(TAG, "inSampleSize for image = "+inSampleSize);
            BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
            bitmapOptions.inSampleSize = inSampleSize;
            input = context.getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, bitmapOptions);
            input.close();
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "getBitmapFromURI exception = "+ e);
        }
        return null;
    }

    public static void extractTextFromImageList(final Context context, final List<Bitmap > imageList,
                                                             Handler handler, final String eBookName, final List<Uri> imageUris) {
        final TextRecognizer recognizer = TextRecognition.getClient();
        handler.post(new Runnable() {
            @Override
            public void run() {
                mCurrDigitalPageList.clear();
                if(imageList.isEmpty()) {
                    for(Uri uri : imageUris) {
                        Bitmap image = getBitmapFromURI(context, uri);
                        Bitmap usableImage = rotateImageIfRequired(context, image, uri);
                        imageList.add(usableImage);
                    }
                }
                for(Bitmap bitmap : imageList) {
                    InputImage image = InputImage.fromBitmap(bitmap, 0);
                    Task<Text> result = recognizer.process(image).addOnSuccessListener(new OnSuccessListener<Text>() {
                        @Override
                        public void onSuccess(Text text) {
                            mResultText = text.getText();
                            DigitalPage page = new DigitalPage();
                            page.setText(mResultText);
                            mCurrDigitalPageList.add(page);
                            if(imageList.size() == mCurrDigitalPageList.size()) {
                                //writeToFile(context, eBookName);
                                mCallback.onTextRecognitionCompleted(eBookName, mCurrDigitalPageList);
                            }
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e(TAG, "Text recognition failed Exception : "+e);
                            Toast.makeText(context, context.getString(R.string.text_recognition_failed_toast_message), Toast.LENGTH_LONG).show();
                            e.printStackTrace();
                        }
                    }). addOnCanceledListener(new OnCanceledListener() {
                        @Override
                        public void onCanceled() {
                            Toast.makeText(context, context.getString(R.string.text_recognition_failed_toast_message), Toast.LENGTH_LONG).show();
                            Log.e(TAG, "Text recognition cancelled");
                        }
                    });
                }
            }
        });
    }

    public static void extractTextFromImagePdf(final Context context, Bitmap bitmap, final int index) {
        mResultText = null;
        final TextRecognizer recognizer = TextRecognition.getClient();
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        Task<Text> result = recognizer.process(image).addOnSuccessListener(new OnSuccessListener<Text>() {
            final int mIndex = index;
            @Override
            public void onSuccess(Text text) {
                mCurrDigitalPageList.get(mIndex).setText(text.getText());
                if(mIndex  >= mLastImagePageIndex) {
                    writePagesToFile();
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(context, context.getString(R.string.text_recognition_failed_toast_message), Toast.LENGTH_LONG).show();
                Log.e(TAG, "Text recognition failed Exception : "+e);
                e.printStackTrace();
            }
        }). addOnCanceledListener(new OnCanceledListener() {
            @Override
            public void onCanceled() {
                Toast.makeText(context, context.getString(R.string.text_recognition_failed_toast_message), Toast.LENGTH_LONG).show();
                Log.e(TAG, "Text recognition cancelled");
            }
        });
    }

    public static void writeToFile(Context context, String bookName) {
        String filePath = ROOT_DIR + "/" + bookName + "/";
        File path = context.getExternalFilesDir(filePath);
        if(!path.exists()) {
            path.mkdirs();
        }
        final File eBook = new File(path, "eBook.txt");
        try {
            if(!eBook.exists()) {
                eBook.createNewFile();
            }
            FileOutputStream fout = new FileOutputStream(eBook);
            FileWriter writer = new FileWriter(eBook);
            for(DigitalPage page : mCurrDigitalPageList) {
                String pageText = isLastPage(page) ? page.getText() : page.getText() + PAGE_SEPARATOR;
                writer.append(pageText);
            }
            writer.close();
            fout.flush();
            fout.close();
            mCallback.onFileWritten(bookName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean isLastPage(DigitalPage page) {
        return mCurrDigitalPageList.indexOf(page) == mCurrDigitalPageList.size() - 1;
    }

    public static String[] readFromFile(Context context, String eBookName, int source) {
        String filePath = "";
        switch (source) {
            case 0:
                filePath = ROOT_DIR + "/" + eBookName + "/";
                break;
            case 1:
                filePath = ROOT_DIR + "/PDF/" + eBookName + "/";
                break;
            case 2:
                break;
            default:
                break;
        }
        File path = context.getExternalFilesDir(filePath);
        if(!path.exists()) {
            Toast.makeText(context, "Something went wrong!", Toast.LENGTH_SHORT).show();
            return null;
        }
        final File eBook = new File(path, "eBook.txt");
        StringBuilder text = new StringBuilder();
        String line;
        String space = " ";
        try {
            if(!eBook.exists()) {
                Toast.makeText(context, "Something went wrong!", Toast.LENGTH_SHORT).show();
                return null;
            }
            BufferedReader reader = new BufferedReader(new FileReader(eBook));
            while((line = reader.readLine()) != null) {
                text.append(line);
                text.append(System.getProperty("line.separator"));
            }
            reader.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
        String content = text.toString();
        String[] pages = content.split(PAGE_SEPARATOR);
        if(mCallback != null) {
            mCallback.onFileRead(eBookName, pages);
        }
        return pages;
    }

    public static String getNameFromURI(Context context, Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] proj = { MediaStore.Files.FileColumns.DATA };
            cursor = context.getContentResolver().query(contentUri,  null, null, null, null);
            if(cursor == null) {
                cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
            }
            int column_index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } catch (Exception e) {
            Log.e(TAG, "getNameFromURI exception : "+e);
            return "Your File";
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public static void extractTextFromFile(Context context, String bookName, Uri uri) {
        String name = (bookName != null && !bookName.isEmpty()) ? bookName : getNameFromURI(context, uri);
        if(name.endsWith("pdf")) {
            extractTextFromPDF(context, bookName, uri);
        }
        if(name.endsWith("docx")) {
            //extractTextFromDoc(context, bookName, uri);
        }
    }

    public static void extractTextFromPDF(Context context, String bookName, Uri uri) {
        String parsedText = null;
        PDDocument document = null;
        String fileNameWithExtension = (bookName != null && !bookName.isEmpty()) ? bookName : getNameFromURI(context, uri);
        String fileName = getFileName(fileNameWithExtension);
        mFileName = fileName;
        String outputFilePath = ROOT_DIR + "/PDF/" + fileName + "/";
        File outputPath = context.getExternalFilesDir(outputFilePath);
        if(!outputPath.exists()) {
            outputPath.mkdirs();
        }
        if(fileNameWithExtension == null) {
            Toast.makeText(context, "Something went wrong!", Toast.LENGTH_SHORT).show();
            return;
        }
        String destPDFPath = makeLocalCopy(context, fileName, fileNameWithExtension, uri);
        File destPDFFile = context.getExternalFilesDir(destPDFPath);
        mOutputFile = new File(outputPath, "eBook.txt");
        try {
            document = PDDocument.load(destPDFFile);
            if(document.isEncrypted()) {
                Toast.makeText(context, "Can't open a password protected document!", Toast.LENGTH_LONG).show();
                mCallback.onError(false, fileName);
                return;
            }
        } catch(IOException e) {
            e.printStackTrace();
            mCallback.onError(true, fileName);
        }
        try {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            pdfStripper.setSortByPosition(true);
            try {
                String text="";
                PdfReader pdfReader = new PdfReader(destPDFFile.toString());
                int n = pdfReader.getNumberOfPages();
                mCurrDigitalPageList.clear();
                for (int i = 0; i < n ; i++) {
                    String extractedText = PdfTextExtractor.getTextFromPage(pdfReader, i+1);
                    DigitalPage digitalPage = new DigitalPage();
                    if(extractedText == null || extractedText.isEmpty()) {
                        Bitmap pageImage = getBitmapFromPage(document, i);
                        digitalPage.setImage(pageImage);
                        mLastImagePageIndex = i;
                    }
                    digitalPage.setText(extractedText);
                    mCurrDigitalPageList.add(digitalPage);
                    mCallback.onPageExtractionComplete(i + 1);
                }
                pdfReader.close();
            } catch (Exception e) {
            }
            document.close();
            getTextFromDigitalPages(context);
        } catch (Exception e) {
            Log.e(TAG, "extractTextFromPDF exception : "+e);
            e.printStackTrace();
        } finally {
            try {
                if (document != null) document.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void getTextFromDigitalPages(Context context) {
        int pageIndex = -1;
        boolean hasImage = false;
        for(DigitalPage page : mCurrDigitalPageList) {
            pageIndex++;
            Bitmap image = page.getImage();
            if(image != null) {
                hasImage = true;
                extractTextFromImagePdf(context, image, pageIndex);
            }
        }
        if(!hasImage) {
            writePagesToFile();
        }
    }

    public static void resetStates() {
        mFileName = null;
        mOutputFile = null;
        mLastImagePageIndex = -1;
    }

    public static void writePagesToFile() {
        try {
            FileWriter writer = new FileWriter(mOutputFile);
            int index = -1;
            for(DigitalPage page : mCurrDigitalPageList) {
                index++;
                String pageText = isLastPage(page) ? page.getText() : page.getText() + PAGE_SEPARATOR;
                pageText = pageText.replaceAll("[\\s&&[^\\n]]+", " ");
                writer.append(pageText);
            }
            writer.close();
            mCallback.onTextExtractionComplete(mFileName);
            resetStates();
        } catch (Exception e) {
            Log.e(TAG, "writePagesToFile exception = "+e);
        }
    }

    public static Bitmap getBitmapFromPage(PDDocument document, int pageIndex) {
        try {
            PDFRenderer renderer = new PDFRenderer(document);
            Bitmap b = renderer.renderImage(pageIndex);
            return b;
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

//    public static void parsePdf(String pdf, String txt) throws IOException {
//        PdfReader reader = new PdfReader(pdf);
//        PdfReaderContentParser parser = new PdfReaderContentParser(reader);
//        PrintWriter out = new PrintWriter(new FileOutputStream(txt));
//        TextExtractionStrategy strategy;
//        String str = "";
//        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
//            strategy = parser.processContent(i, new SimpleTextExtractionStrategy());
//            str = str + strategy.getResultantText() + PAGE_SEPARATOR;
//            out.append(str);
//            mCallback.onPageExtractionComplete(i + 1);
//        }
//        reader.close();
//        out.flush();
//        out.close();
//    }

    public static String getFileName(String name) {
        int dotPosition = name.lastIndexOf(".");
        return name.substring(0, dotPosition);
    }

    public static List<String> fetchEbookNames(Context context) {
        List<String> fileNames = new ArrayList<>();
        String outputFilePath = ROOT_DIR + "/PDF/";
        File outputPath = context.getExternalFilesDir(outputFilePath);
        if(!outputPath.exists()) {
            outputPath.mkdirs();
        }
        File[] files = outputPath.listFiles();
        for(File file : files) {
            if(file.isDirectory()) {
                fileNames.add(file.getName());
            }
        }
        return fileNames;
    }

    public static void deleteEbook(Context context, String name) {
        String root = ROOT_DIR + "/PDF/" + name;
        File path = context.getExternalFilesDir(root);
        deleteEbook(path);
        SharedPreferences prefs = context.getSharedPreferences(EBOOK_STATE_PREFERENCE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(PAGE_NUMBER_PREFERENCE_HEAD + name);
        editor.apply();
    }

    public static void deleteEbook(File path) {
        if(!path.exists()) {
            path.mkdirs();
        }
        File[] files = path.listFiles();
        for(File file : files) {
            if(file.isDirectory()) {
                deleteEbook(file);
            } else {
                file.delete();
            }
        }
        path.delete();
    }

    public static void searchOnGoogle(Context context, String word) {
        String escapedQuery;
        try {
            escapedQuery = URLEncoder.encode(word, "UTF-8") + " meaning";
        } catch (Exception e) {
            escapedQuery = word + " meaning";
        }
        Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
        intent.putExtra(SearchManager.QUERY, escapedQuery);
        context.startActivity(intent);
//        Uri uri = Uri.parse("http://www.google.com/#q=" + escapedQuery);
//        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
//        context.startActivity(intent);
    }

    public static String getFullWordType(String type) {
        switch (type) {
            case "n.":
                return "noun ";
            case "a.":
                return "adjective ";
            case "v. t." :
                return "transitive verb ";
            case "adv." :
                return "adverb ";
            case "pl." :
                return "plural ";
            case "prep." :
                return "preposition ";
            case "conj." :
                return "conjunction ";
            case "v." :
                return "verb ";
        }
        return type;
    }

    public static void updateIfPossible(final Context context) {
        mAppUpdateManager = AppUpdateManagerFactory.create(context);
        com.google.android.play.core.tasks.Task<AppUpdateInfo> appUpdateInfoTask = mAppUpdateManager.getAppUpdateInfo();
        appUpdateInfoTask.addOnSuccessListener(new com.google.android.play.core.tasks.OnSuccessListener<AppUpdateInfo>() {
            @Override
            public void onSuccess(AppUpdateInfo appUpdateInfo) {
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                        && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    try {
                        mAppUpdateManager.startUpdateFlowForResult(
                                appUpdateInfo,
                                AppUpdateType.IMMEDIATE,
                                (MainActivity)context,
                                APP_UPDATE_REQUEST_CODE);
                    } catch (IntentSender.SendIntentException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @author paulburke
     */
    public static String getPath(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] {
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    public static String makeLocalCopy(Context context, String fileName, String fileNameWithExtension, Uri uri) {
        String destinationPath = ROOT_DIR + "/PDF/" + fileName + "/";
        File pdfRoot = context.getExternalFilesDir(destinationPath);
        File pdfFile = new File(pdfRoot, fileNameWithExtension);
        if(!pdfRoot.exists()) {
            pdfRoot.mkdirs();
        }
        try {
            if(!pdfFile.exists()) {
                pdfFile.createNewFile();
            }
            InputStream inputStream= context.getContentResolver().openInputStream(uri);
            //FileInputStream inputStream = new FileInputStream(source);
            OutputStream outputStream = new FileOutputStream(pdfFile);
            byte[] buf = new byte[1024];
            while(inputStream.read(buf) > 0) {
                outputStream.write(buf);
            }
            inputStream.close();
            outputStream.flush();
            outputStream.close();
        } catch (Exception e) {
        }
        return destinationPath + fileNameWithExtension;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @param selection (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    public static Cursor fetchAllDocs(Context context) {
        String pdf = MimeTypeMap.getSingleton().getMimeTypeFromExtension("pdf");
        Uri table = MediaStore.Files.getContentUri("external");
        String[] column = {MediaStore.Files.FileColumns.DATA};
        String where = MediaStore.Files.FileColumns.MIME_TYPE + "=?";
        String[] args = {pdf};
        String sortOrder = MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC";
        return context.getContentResolver().query(table, null, where, args, sortOrder);
    }


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }
}
