package net.vheerden.archi.mcp.model;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.widgets.Display;

import com.archimatetool.editor.diagram.util.DiagramUtils;
import com.archimatetool.export.svg.PDFExportProvider;
import com.archimatetool.export.svg.SVGExportProvider;
import com.archimatetool.model.IArchimateDiagramModel;

import net.vheerden.archi.mcp.response.ErrorCode;
import net.vheerden.archi.mcp.response.dto.ExportViewResultDto;

/**
 * Handles view export to PNG, JPG, SVG, and PDF formats.
 *
 * <p>Extracted from ArchiModelAccessorImpl (Story 12-4) to improve cohesion.
 * Package-visible — only ArchiModelAccessorImpl should use this class.</p>
 *
 * <p><strong>Bundle dependencies (Story 14-4):</strong> SVG and PDF rendering
 * require the optional {@code com.archimatetool.export.svg} bundle (declared
 * with {@code resolution:=optional} in MANIFEST.MF). PNG and JPG use core SWT
 * and have no external bundle dependency. When the SVG bundle is absent the
 * {@link Platform#getBundle(String)} guard short-circuits before any typed
 * reference is touched.</p>
 */
final class ViewExportService {

    private ViewExportService() {}

    static ExportResult renderPng(IArchimateDiagramModel diagramModel,
                            double scale, boolean inline, String outputDirectory) {
        // Validate output directory before rendering (fail fast — don't waste CPU)
        if (!inline) {
            validateOutputDirectory(outputDirectory);
        }

        long startTime = System.currentTimeMillis();
        AtomicReference<byte[]> pngBytesRef = new AtomicReference<>();
        AtomicReference<Integer> widthRef = new AtomicReference<>();
        AtomicReference<Integer> heightRef = new AtomicReference<>();
        AtomicReference<RuntimeException> errorRef = new AtomicReference<>();

        Display.getDefault().syncExec(() -> {
            Image image = null;
            try {
                image = DiagramUtils.createImage(diagramModel, scale, 10);
                ImageData imageData = image.getImageData();
                widthRef.set(imageData.width);
                heightRef.set(imageData.height);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageLoader loader = new ImageLoader();
                loader.data = new ImageData[] { imageData };
                loader.save(baos, SWT.IMAGE_PNG);
                pngBytesRef.set(baos.toByteArray());
            } catch (Throwable t) {
                errorRef.set(new RuntimeException(t));
            } finally {
                if (image != null) {
                    image.dispose();
                }
            }
        });

        if (errorRef.get() != null) {
            throw new ModelAccessException(
                    "PNG rendering failed: " + errorRef.get().getMessage(),
                    errorRef.get(), ErrorCode.INTERNAL_ERROR);
        }

        long renderTimeMs = System.currentTimeMillis() - startTime;
        byte[] pngBytes = pngBytesRef.get();
        String filePath = null;

        if (!inline) {
            filePath = writeToFile(pngBytes, diagramModel.getId(), "png", outputDirectory);
        }

        ExportViewResultDto metadata = new ExportViewResultDto(
                diagramModel.getId(),
                diagramModel.getName(),
                "png",
                "image/png",
                widthRef.get(),
                heightRef.get(),
                filePath,
                renderTimeMs);

        return new ExportResult(metadata, inline ? pngBytes : null, null);
    }

    static ExportResult renderJpg(IArchimateDiagramModel diagramModel,
                            double scale, int quality, boolean inline, String outputDirectory) {
        if (!inline) {
            validateOutputDirectory(outputDirectory);
        }

        long startTime = System.currentTimeMillis();
        AtomicReference<byte[]> jpgBytesRef = new AtomicReference<>();
        AtomicReference<Integer> widthRef = new AtomicReference<>();
        AtomicReference<Integer> heightRef = new AtomicReference<>();
        AtomicReference<RuntimeException> errorRef = new AtomicReference<>();

        Display.getDefault().syncExec(() -> {
            Image image = null;
            try {
                image = DiagramUtils.createImage(diagramModel, scale, 10);
                ImageData imageData = image.getImageData();
                widthRef.set(imageData.width);
                heightRef.set(imageData.height);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageLoader loader = new ImageLoader();
                loader.data = new ImageData[] { imageData };
                // SWT's ImageLoader.compression is a scalar int (1-100 for JPEG),
                // not an int[] as the Story 14-4 Dev Notes pseudocode suggested
                // (verified against SWT 3.126 on Archi 5.8).
                loader.compression = quality;
                loader.save(baos, SWT.IMAGE_JPEG);
                jpgBytesRef.set(baos.toByteArray());
            } catch (Throwable t) {
                errorRef.set(new RuntimeException(t));
            } finally {
                if (image != null) {
                    image.dispose();
                }
            }
        });

        if (errorRef.get() != null) {
            throw new ModelAccessException(
                    "JPG rendering failed: " + errorRef.get().getMessage(),
                    errorRef.get(), ErrorCode.INTERNAL_ERROR);
        }

        long renderTimeMs = System.currentTimeMillis() - startTime;
        byte[] jpgBytes = jpgBytesRef.get();
        String filePath = null;

        if (!inline) {
            filePath = writeToFile(jpgBytes, diagramModel.getId(), "jpg", outputDirectory);
        }

        ExportViewResultDto metadata = new ExportViewResultDto(
                diagramModel.getId(),
                diagramModel.getName(),
                "jpg",
                "image/jpeg",
                widthRef.get(),
                heightRef.get(),
                filePath,
                renderTimeMs);

        return new ExportResult(metadata, inline ? jpgBytes : null, null);
    }

    static ExportResult renderSvg(IArchimateDiagramModel diagramModel,
                            double scale, boolean inline, String outputDirectory) {
        // scale: not forwarded — SVGExportProvider.getSVGString(IDiagramModel,
        // boolean) has no scale overload; SVG is vector + resolution-independent.
        if (Platform.getBundle("com.archimatetool.export.svg") == null) {
            throw new ModelAccessException(
                    "SVG export is not available. The SVG export plugin "
                            + "(com.archimatetool.export.svg) is not installed.",
                    ErrorCode.FORMAT_NOT_AVAILABLE,
                    "Install the Archi SVG export plugin or use format 'png' instead",
                    "Use export-view with format 'png'",
                    null);
        }
        if (!inline) {
            validateOutputDirectory(outputDirectory);
        }

        long startTime = System.currentTimeMillis();
        AtomicReference<String> svgRef = new AtomicReference<>();
        AtomicReference<RuntimeException> errorRef = new AtomicReference<>();

        // boolean true = include viewbox attribute — matches the Archi GUI default
        // (SVGExportProvider.fSetViewboxButton initialised true via preferences).
        Display.getDefault().syncExec(() -> {
            try {
                SVGExportProvider provider = new SVGExportProvider();
                svgRef.set(provider.getSVGString(diagramModel, true));
            } catch (Throwable t) {
                errorRef.set(new RuntimeException(t));
            }
        });

        if (errorRef.get() != null) {
            throw new ModelAccessException(
                    "SVG rendering failed: " + errorRef.get().getMessage(),
                    errorRef.get(), ErrorCode.INTERNAL_ERROR);
        }

        long renderTimeMs = System.currentTimeMillis() - startTime;
        String svg = svgRef.get();
        String filePath = null;
        if (!inline) {
            filePath = writeToFile(
                    svg.getBytes(StandardCharsets.UTF_8),
                    diagramModel.getId(),
                    "svg",
                    outputDirectory);
        }

        ExportViewResultDto metadata = new ExportViewResultDto(
                diagramModel.getId(),
                diagramModel.getName(),
                "svg",
                "image/svg+xml",
                null,
                null,
                filePath,
                renderTimeMs);

        return new ExportResult(metadata, null, inline ? svg : null);
    }

    static ExportResult renderPdf(IArchimateDiagramModel diagramModel,
                            double scale, boolean inline, String outputDirectory) {
        // scale: not forwarded — PDFExportProvider.export(IDiagramModel, File) has
        // no scale overload; vector PDF is resolution-independent and the tool
        // description documents this. Story 14-4 Dev Notes §"Predicted Task-0
        // disposition" rationale.
        if (Platform.getBundle("com.archimatetool.export.svg") == null) {
            throw new ModelAccessException(
                    "PDF export is not available. The Archi SVG export plugin "
                            + "(com.archimatetool.export.svg) is not installed — "
                            + "PDF support is provided by the same bundle.",
                    ErrorCode.FORMAT_NOT_AVAILABLE,
                    "Install the Archi SVG export plugin or use format 'png' instead",
                    "Use export-view with format 'png'",
                    null);
        }
        if (!inline) {
            validateOutputDirectory(outputDirectory);
        }

        long startTime = System.currentTimeMillis();
        AtomicReference<byte[]> pdfBytesRef = new AtomicReference<>();
        AtomicReference<RuntimeException> errorRef = new AtomicReference<>();

        // Inside syncExec: SWT-thread-only work (PDF provider dispatch + temp-file
        // read). Outside syncExec: bulk file I/O for the file-mode output directory,
        // mirroring the renderPng / renderSvg pattern. Per cross-LLM review (Sonnet
        // 4.6, 2026-05-27) M1: writeToFile on the Display thread would freeze the
        // Archi GUI for multi-MB PDFs.
        Display.getDefault().syncExec(() -> {
            File tempPdf = null;
            try {
                tempPdf = File.createTempFile("archi-mcp-pdf-", ".pdf");
                PDFExportProvider provider = new PDFExportProvider();
                provider.export(diagramModel, tempPdf);
                pdfBytesRef.set(Files.readAllBytes(tempPdf.toPath()));
            } catch (Throwable t) {
                errorRef.set(new RuntimeException(t));
            } finally {
                if (tempPdf != null && tempPdf.exists()) {
                    tempPdf.delete();
                }
            }
        });

        if (errorRef.get() != null) {
            throw new ModelAccessException(
                    "PDF rendering failed: " + errorRef.get().getMessage(),
                    errorRef.get(), ErrorCode.INTERNAL_ERROR);
        }

        long renderTimeMs = System.currentTimeMillis() - startTime;
        byte[] pdfBytes = pdfBytesRef.get();
        String filePath = null;
        if (!inline) {
            filePath = writeToFile(pdfBytes, diagramModel.getId(), "pdf",
                    outputDirectory);
        }

        ExportViewResultDto metadata = new ExportViewResultDto(
                diagramModel.getId(),
                diagramModel.getName(),
                "pdf",
                "application/pdf",
                null,
                null,
                filePath,
                renderTimeMs);

        return new ExportResult(metadata, inline ? pdfBytes : null, null);
    }

    /**
     * Validates the output directory before rendering. If the directory exists,
     * checks writability. If it doesn't exist, checks that parent is writable.
     * Skips validation for temp directory (null/blank outputDirectory).
     */
    private static void validateOutputDirectory(String outputDirectory) {
        if (outputDirectory == null || outputDirectory.isBlank()) {
            return; // temp dir — validated during writeToFile
        }
        java.nio.file.Path dir = java.nio.file.Path.of(outputDirectory);
        if (dir.toFile().exists()) {
            if (!dir.toFile().canWrite()) {
                throw new ModelAccessException(
                        "Output directory is not writable: " + dir,
                        ErrorCode.INVALID_PARAMETER,
                        null,
                        "Provide a writable directory path or omit outputDirectory to use the temp directory",
                        null);
            }
        } else {
            // Directory doesn't exist — check nearest existing ancestor for writability
            java.nio.file.Path ancestor = dir.getParent();
            while (ancestor != null && !ancestor.toFile().exists()) {
                ancestor = ancestor.getParent();
            }
            if (ancestor != null && !ancestor.toFile().canWrite()) {
                throw new ModelAccessException(
                        "Output directory is not writable: " + dir
                                + " (parent " + ancestor + " is not writable)",
                        ErrorCode.INVALID_PARAMETER,
                        null,
                        "Provide a writable directory path or omit outputDirectory to use the temp directory",
                        null);
            }
        }
    }

    private static String writeToFile(byte[] data, String viewId, String extension,
            String outputDirectory) {
        java.nio.file.Path exportDir;
        if (outputDirectory == null || outputDirectory.isBlank()) {
            exportDir = java.nio.file.Path.of(
                    System.getProperty("java.io.tmpdir"), "archi-mcp-export");
        } else {
            exportDir = java.nio.file.Path.of(outputDirectory);
        }

        try {
            java.nio.file.Files.createDirectories(exportDir);
        } catch (IOException e) {
            throw new ModelAccessException(
                    "Failed to create output directory: " + e.getMessage(),
                    e, ErrorCode.INTERNAL_ERROR);
        }

        if (!exportDir.toFile().canWrite()) {
            throw new ModelAccessException(
                    "Output directory is not writable: " + exportDir,
                    ErrorCode.INVALID_PARAMETER,
                    null,
                    "Provide a writable directory path or omit outputDirectory to use the temp directory",
                    null);
        }

        String fileName = viewId + "_" + System.currentTimeMillis() + "." + extension;
        File outputFile = exportDir.resolve(fileName).toFile();
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(data);
        } catch (IOException e) {
            throw new ModelAccessException(
                    "Failed to write export file: " + e.getMessage(),
                    e, ErrorCode.INTERNAL_ERROR);
        }
        return outputFile.getAbsolutePath();
    }
}
