package com.nekkochan.onyxchat.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;

import com.itextpdf.text.Document;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
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

            // Try to convert the document using Apache POI and iText/PDFBox
            try {
                success = convertWithPOIAndPDF(inputStream, outputFile, extension);
            } catch (Exception e) {
                Log.e(TAG, "Document conversion failed", e);
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
     * Convert a document to PDF using Apache POI with iText or PDFBox
     *
     * @param inputStream The input stream of the document
     * @param outputFile  The output PDF file
     * @param extension   The file extension of the document
     * @return True if conversion was successful, false otherwise
     */
    private static boolean convertWithPOIAndPDF(InputStream inputStream, File outputFile, String extension) throws Exception {
        switch (extension.toLowerCase()) {
            case "doc":
                // Convert DOC using HWPFDocument
                HWPFDocument doc = new HWPFDocument(inputStream);
                String docText = doc.getDocumentText();
                return convertWordDocToPdf(docText, outputFile);
                
            case "docx":
                // Convert DOCX using XWPFDocument
                XWPFDocument docx = new XWPFDocument(inputStream);
                StringBuilder docxText = new StringBuilder();
                docx.getParagraphs().forEach(paragraph -> docxText.append(paragraph.getText()).append("\n"));
                return convertWordDocToPdf(docxText.toString(), outputFile);
                
            case "odt":
            case "rtf":
                // For ODT and RTF, we'll just create a simple PDF with a message
                return createSimplePdf(outputFile, "This document format requires additional processing.");
                
            case "xls":
                // Convert XLS using HSSFWorkbook
                HSSFWorkbook xls = new HSSFWorkbook(inputStream);
                return createSimplePdf(outputFile, "Excel document with " + xls.getNumberOfSheets() + " sheets.");
                
            case "xlsx":
                // Convert XLSX using XSSFWorkbook
                XSSFWorkbook xlsx = new XSSFWorkbook(inputStream);
                return createSimplePdf(outputFile, "Excel document with " + xlsx.getNumberOfSheets() + " sheets.");
                
            case "ppt":
                // Convert PPT using HSLFSlideShow
                HSLFSlideShow ppt = new HSLFSlideShow(inputStream);
                return createSimplePdf(outputFile, "PowerPoint presentation with " + ppt.getSlides().size() + " slides.");
                
            case "pptx":
                // Convert PPTX using XMLSlideShow
                XMLSlideShow pptx = new XMLSlideShow(inputStream);
                return createSimplePdf(outputFile, "PowerPoint presentation with " + pptx.getSlides().size() + " slides.");
                
            default:
                // For unsupported formats, create a simple PDF with a message
                return createSimplePdf(outputFile, "This document format (" + extension + ") is not supported for preview.");
        }
    }
    
    /**
     * Convert text from a Word document to PDF using iText
     *
     * @param text       The text content to convert
     * @param outputFile The output PDF file
     * @return True if conversion was successful, false otherwise
     */
    private static boolean convertWordDocToPdf(String text, File outputFile) throws Exception {
        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(outputFile));
        document.open();
        document.add(new Paragraph(text));
        document.close();
        return true;
    }
    
    /**
     * Create a simple PDF with a message using PDFBox
     *
     * @param outputFile The output PDF file
     * @param message    The message to include in the PDF
     * @return True if creation was successful, false otherwise
     */
    private static boolean createSimplePdf(File outputFile, String message) throws Exception {
        PDDocument document = new PDDocument();
        PDPage page = new PDPage();
        document.addPage(page);
        
        PDPageContentStream contentStream = new PDPageContentStream(document, page);
        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12);
        contentStream.newLineAtOffset(100, 700);
        contentStream.showText(message);
        contentStream.endText();
        contentStream.close();
        
        document.save(outputFile);
        document.close();
        return true;
    }
}
