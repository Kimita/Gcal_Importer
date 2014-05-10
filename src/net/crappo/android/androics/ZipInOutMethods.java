package net.crappo.android.androics;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
//import java.io.InputStream;
//import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
//import java.util.zip.ZipOutputStream;

import android.util.Log;

/*
 * Google Calendarから取得するicalzipを一時保存し、所定のpathへ展開する処理を行うクラス。
 * 本アプリ内でICSデータファイルを持つサブディレクトリの構成もここで保持しておく。
 */
public class ZipInOutMethods {
    private static final String TAG = "ZipInOutMethods";

    static final String extractDir = "/Extracted/";
    static final String outputDir = "/Archive/";
    public String pathToExtractDir;
    public String pathToOutputDir;

//    private static String archiveName = "compress.zip";

    public ZipInOutMethods(String pathToFiles) {
        pathToExtractDir = pathToFiles + extractDir;
        File file = new File(pathToExtractDir);
        try { // zipファイルの展開先ディレクトリを準備する
            if (file.exists()) {    // 同名のファイルが存在してないかどうか
                if (file.isDirectory())    Log.v(TAG, "ZIP extract Path: " + pathToExtractDir);
                else                    Log.e(TAG, "Error: File exists. - " + pathToExtractDir);
            } else {                // ディレクトリ作成に失敗しないかどうか
                if (!file.mkdir())        Log.e(TAG, "Error: Can not create directory - '" + pathToExtractDir + "'.");
                else                    Log.v(TAG, "ZIP extract Path: " + pathToExtractDir);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
        pathToOutputDir = pathToFiles + outputDir;
        file = new File(pathToOutputDir);
        try { // icalzipをWebから取得する時の保存先を用意しておく
            if (file.exists()) {
                if (file.isDirectory())    Log.v(TAG, "ZIP output Path: " + pathToOutputDir);
                else                    Log.e(TAG, "Error: File exists. - " + pathToOutputDir);
            } else {
                if (!file.mkdir())        Log.e(TAG, "Error: Can not create directory - '" + pathToOutputDir + "'.");
                else                    Log.v(TAG, "ZIP output Path: " + pathToOutputDir);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * Activityクラスから、zipファイルを展開したい時に呼ばれるメソッド。
     * 対象のzipファイル名を受け取って、所定のディレクトリに展開する。
     */
    public boolean extractDownloadedZip(String fname) {
        String targetPath = pathToOutputDir + fname;
        File targetFile = new File(targetPath);
        boolean res = false;

        ZipInputStream zipIn = null;
        BufferedOutputStream buffOutStream = null;
        ZipEntry zipEntry = null;
        int writeLength = 0;
        try {
            zipIn = new ZipInputStream(new FileInputStream(targetFile));
            while ((zipEntry = zipIn.getNextEntry()) != null) {
                buffOutStream = new BufferedOutputStream(new FileOutputStream(pathToExtractDir + zipEntry.getName()));
                byte[] buff1Kb = new byte[1024];
                while ((writeLength = zipIn.read(buff1Kb)) != -1) {
                    buffOutStream.write(buff1Kb, 0, writeLength);
                }
                zipIn.closeEntry();
                buffOutStream.close();
                buffOutStream = null;
            }
            zipIn.close();
            res = true;
            if(targetFile.delete())    Log.v(TAG, "File Deleted.");
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Open Error: [File:" + targetPath + "]");
            e.printStackTrace();
        } catch (IOException e) {
            Log.e(TAG, "Can not extract [File:" + targetPath + "].");
            e.printStackTrace();
        }
        return res;
    }

//    // via http://techbooster.org/android/application/15261/
//    public void compress(List<File> inputFiles, String outputFile) {
//        InputStream is = null;
//        ZipOutputStream zos = null;
//        byte[] buf = new byte[1024];
//        try {
//            zos = new ZipOutputStream(new FileOutputStream(outputFile));
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }
//
//        try {
//            for (int i = 0; i < inputFiles.size(); i++) {
//                File fileObj = inputFiles.get(i);
//                String filepath = fileObj.getCanonicalPath();
//                is = new FileInputStream(filepath);
//                String filename = String.format(lc, fileObj.getPath());
//                Log.v(TAG, "filename : " + filename);
//                ZipEntry ze = new ZipEntry(filename);
//                zos.putNextEntry(ze);
//                int len = 0;
//                while ((len = is.read(buf)) != -1) {
//                    zos.write(buf, 0, len);
//                }
//                is.close();
//                zos.closeEntry();
//            }
//            zos.close();
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

}
