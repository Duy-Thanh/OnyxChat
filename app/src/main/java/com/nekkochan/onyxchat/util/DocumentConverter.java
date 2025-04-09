package com.nekkochan.onyxchat.util;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.aspose.words.Document;
import com.aspose.words.SaveFormat;
import com.aspose.cells.Workbook;
import com.aspose.slides.Presentation;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Utility class for converting various document formats to PDF
 */
public class DocumentConverter {
    private static final String TAG = "DocumentConverter";

    /**
     * Convert a document to PDF format
     *
     * @param context   The context
     * @param uri       The URI of the document to convert
     * @param extension The file extension of the document
     * @return The converted PDF file, or null if conversion failed
     */
    public static File convertToPdf(Context context, Uri uri, String extension) {
        try {
            // Create a temporary file for the PDF output
            File outputDir = context.getCacheDir();
            File outputFile = new File(outputDir, UUID.randomUUID().toString() + ".pdf");

            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream for URI: " + uri);
                return null;
            }

            boolean success = false;

            // Try using Aspose libraries first (more reliable but commercial)
            try {
                success = convertWithAspose(inputStream, outputFile, extension);
            } catch (Exception e) {
                Log.w(TAG, "Aspose conversion failed, falling back to Apache POI", e);
            }

            // If Aspose failed, try Apache POI (open source but limited)
            if (!success) {
                // Reset the input stream
                inputStream.close();
                inputStream = context.getContentResolver().openInputStream(uri);
                success = convertWithApachePOI(inputStream, outputFile, extension);
            }

            inputStream.close();

            if (success && outputFile.exists() && outputFile.length() > 0) {
                return outputFile;
            } else {
                Log.e(TAG, "Conversion failed or output file is empty");
                if (outputFile.exists()) {
                    outputFile.delete();
                }
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error converting document to PDF", e);
            return null;
        }
    }

    /**
     * Convert a document to PDF using Aspose libraries
     *
     * @param inputStream The input stream of the document
     * @param outputFile  The output PDF file
     * @param extension   The file extension of the document
     * @return True if conversion was successful, false otherwise
     */
    private static boolean convertWithAspose(InputStream inputStream, File outputFile, String extension) throws Exception {
        switch (extension.toLowerCase()) {
            case "doc":
            case "docx":
            case "odt":
            case "rtf":
                // Convert Word documents
                Document doc = new Document(inputStream);
                doc.save(outputFile.getAbsolutePath(), SaveFormat.PDF);
                return true;

            case "xls":
            case "xlsx":
            case "ods":
                // Convert Excel documents
                Workbook workbook = new Workbook(inputStream);
                workbook.save(outputFile.getAbsolutePath(), com.aspose.cells.SaveFormat.PDF);
                return true;

            case "ppt":
            case "pptx":
            case "odp":
                // Convert PowerPoint documents
                Presentation presentation = new Presentation(inputStream);
                presentation.save(outputFile.getAbsolutePath(), com.aspose.slides.SaveFormat.Pdf);
                return true;

            default:
                return false;
        }
    }

    /**
     * Convert a document to PDF using Apache POI
     *
     * @param inputStream The input stream of the document
     * @param outputFile  The output PDF file
     * @param extension   The file extension of the document
     * @return True if conversion was successful, false otherwise
     */
    private static boolean convertWithApachePOI(InputStream inputStream, File outputFile, String extension) throws Exception {
        // Apache POI doesn't have direct PDF conversion capabilities
        // This would require additional libraries like iText or PDFBox
        // For simplicity, we'll just return false here
        // In a real implementation, you would integrate with a PDF generation library
        
        Log.w(TAG, "Apache POI direct PDF conversion not implemented");
        return false;
    }
}
