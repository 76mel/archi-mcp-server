package net.vheerden.archi.mcp.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.eclipse.swt.graphics.ImageData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.archimatetool.editor.model.IArchiveManager;
import com.archimatetool.model.IArchimateModel;

import net.vheerden.archi.mcp.response.ErrorCode;
import net.vheerden.archi.mcp.response.dto.AddImageResultDto;
import net.vheerden.archi.mcp.response.dto.ModelImageDto;

/**
 * Model-side image operations: add an image to the model archive (from raw
 * bytes or a local file) and list the archive's images.
 *
 * <p>A cohesive cluster peeled out of {@code ArchiModelAccessorImpl} behind its
 * unchanged interface: the accessor keeps one-line forwards to an instance of
 * this class. The methods here reach the model only through the injected
 * {@code Supplier<IArchimateModel>} (the same seam pattern the accessor uses for
 * {@code MutationDispatcher}) plus {@code IArchiveManager} — no back-reference to
 * the accessor, no shared mutable state.</p>
 *
 * <p>Archive writes are not undoable (no command-stack entry); this matches
 * Archi's own behaviour — see {@link #storeImageFile}.</p>
 *
 * <p>Note: the URL-fetch path ({@code addImageFromUrl}) deliberately stays in the
 * accessor — its bounded streaming download is a separately unit-tested static
 * seam, and relocating it is out of scope for this behaviour-preserving move.</p>
 */
final class ImageOperations {

    private static final Logger logger = LoggerFactory.getLogger(ImageOperations.class);

    /** Supplies the currently active model, validating one is loaded (throws if none). */
    private final Supplier<IArchimateModel> modelSupplier;

    ImageOperations(Supplier<IArchimateModel> modelSupplier) {
        this.modelSupplier = modelSupplier;
    }

    AddImageResultDto addImageToModel(String sessionId, byte[] imageData, String filenameHint) {
        logger.info("Adding image to model: filenameHint={}, dataSize={}", filenameHint, imageData != null ? imageData.length : 0);
        IArchimateModel model = modelSupplier.get();

        if (imageData == null || imageData.length == 0) {
            throw new ModelAccessException(
                    "Image data must not be empty",
                    ErrorCode.INVALID_PARAMETER,
                    null,
                    "Provide base64-encoded image data (PNG, JPEG, GIF, BMP, ICO, TIFF).",
                    null);
        }

        if (filenameHint == null || filenameHint.isBlank()) {
            filenameHint = "image.png";
        }

        // Detect extension from filename hint
        String ext = filenameHint.contains(".")
                ? filenameHint.substring(filenameHint.lastIndexOf('.') + 1).toLowerCase()
                : "png";

        // Write to temp file, validate, and store via addImageFromFile
        File tempFile = null;
        try {
            tempFile = File.createTempFile("archi-mcp-image-", "." + ext);
            Files.write(tempFile.toPath(), imageData);
            return storeImageFile(tempFile, model);
        } catch (Exception e) {
            if (e instanceof ModelAccessException) throw (ModelAccessException) e;
            if (e instanceof org.eclipse.swt.SWTException) {
                throw new ModelAccessException(
                        "Invalid or unsupported image data: " + e.getMessage(),
                        ErrorCode.INVALID_PARAMETER,
                        e.getMessage(),
                        "Provide valid image data in a supported format: PNG, JPEG, GIF, BMP, ICO, TIFF. SVG is not supported.",
                        null);
            }
            throw new ModelAccessException(
                    "Failed to add image to model: " + e.getMessage(),
                    ErrorCode.INTERNAL_ERROR,
                    e.getMessage(),
                    null,
                    null);
        } finally {
            if (tempFile != null) {
                tempFile.delete();
            }
        }
    }

    AddImageResultDto addImageFromFilePath(String sessionId, String filePath) {
        logger.info("Adding image from file path: {}", filePath);
        IArchimateModel model = modelSupplier.get();

        if (filePath == null || filePath.isBlank()) {
            throw new ModelAccessException(
                    "filePath must not be empty",
                    ErrorCode.INVALID_PARAMETER,
                    null,
                    "Provide an absolute path to a local image file.",
                    null);
        }

        File file = new File(filePath);
        if (!file.isAbsolute()) {
            throw new ModelAccessException(
                    "filePath must be an absolute path: " + filePath,
                    ErrorCode.INVALID_PARAMETER,
                    null,
                    "Provide an absolute path (e.g., /Users/me/icons/aws-eks.png).",
                    null);
        }
        if (!file.exists()) {
            throw new ModelAccessException(
                    "File not found: " + filePath,
                    ErrorCode.INVALID_PARAMETER,
                    null,
                    "Check the file path and ensure the file exists.",
                    null);
        }
        if (!file.isFile()) {
            throw new ModelAccessException(
                    "Path is not a regular file: " + filePath,
                    ErrorCode.INVALID_PARAMETER,
                    null,
                    "Provide a path to a file, not a directory.",
                    null);
        }
        if (!file.canRead()) {
            throw new ModelAccessException(
                    "File is not readable: " + filePath,
                    ErrorCode.INVALID_PARAMETER,
                    null,
                    "Check file permissions.",
                    null);
        }
        if (file.length() > 1_048_576) {
            throw new ModelAccessException(
                    "File exceeds 1MB limit (" + file.length() + " bytes): " + filePath,
                    ErrorCode.INVALID_PARAMETER,
                    null,
                    "Provide a smaller image (max 1MB).",
                    null);
        }

        try {
            return storeImageFile(file, model);
        } catch (Exception e) {
            if (e instanceof ModelAccessException) throw (ModelAccessException) e;
            if (e instanceof org.eclipse.swt.SWTException) {
                throw new ModelAccessException(
                        "Invalid or unsupported image file: " + e.getMessage(),
                        ErrorCode.INVALID_PARAMETER,
                        e.getMessage(),
                        "Provide a valid image file in a supported format: PNG, JPEG, GIF, BMP, ICO, TIFF. SVG is not supported.",
                        null);
            }
            throw new ModelAccessException(
                    "Failed to add image from file: " + e.getMessage(),
                    ErrorCode.INTERNAL_ERROR,
                    e.getMessage(),
                    null,
                    null);
        }
    }

    /**
     * Validates an image file and stores it in the model's archive via IArchiveManager.addImageFromFile().
     * Returns the result DTO with archive path, dimensions, and format.
     *
     * @param file  the image file to validate and store
     * @param model the model to store the image in (caller already validated via the model supplier)
     */
    // Package-visible: also reused by ArchiModelAccessorImpl.addImageFromUrl (which keeps its
    // separately-tested bounded download seam) via the accessor's imageOps field.
    AddImageResultDto storeImageFile(File file, IArchimateModel model) throws IOException {
        // Validate image by loading ImageData (throws SWTException for invalid/unsupported formats)
        ImageData imgData = new ImageData(file.getAbsolutePath());
        int width = imgData.width;
        int height = imgData.height;

        // Detect format from file extension
        String fileName = file.getName();
        String ext = fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase()
                : "png";
        String formatDetected = switch (ext) {
            case "jpg", "jpeg" -> "JPEG";
            case "gif" -> "GIF";
            case "bmp" -> "BMP";
            case "ico" -> "ICO";
            case "tiff", "tif" -> "TIFF";
            default -> "PNG";
        };

        // Store via IArchiveManager.addImageFromFile — uses Archi's standard archive path naming.
        // NOTE: Archive writes are NOT undoable (no command stack entry). This matches
        // Archi's own behavior — images persist in the archive even if the referencing
        // view object change is undone. Deduplication prevents unbounded orphan growth.
        IArchiveManager archiveManager = (IArchiveManager) model.getAdapter(IArchiveManager.class);
        if (archiveManager == null) {
            throw new ModelAccessException(
                    "Archive manager not available — model may not be saved yet",
                    ErrorCode.INTERNAL_ERROR,
                    null,
                    "Save the model once in Archi before adding images.",
                    null);
        }
        String imagePath = archiveManager.addImageFromFile(file);

        logger.info("Image added to model: path={}, size={}x{}, format={}", imagePath, width, height, formatDetected);
        return new AddImageResultDto(imagePath, width, height, formatDetected);
    }

    List<ModelImageDto> listModelImages(String sessionId) {
        logger.info("Listing model images");
        IArchimateModel model = modelSupplier.get();

        IArchiveManager archiveManager = (IArchiveManager) model.getAdapter(IArchiveManager.class);
        if (archiveManager == null) {
            return List.of();
        }

        // Use getLoadedImagePaths() to include all images in the archive cache,
        // not just those currently referenced by model objects (getImagePaths()).
        // Newly added images live in the cache before any view object references them.
        java.util.Set<String> imagePaths = archiveManager.getLoadedImagePaths();
        if (imagePaths == null || imagePaths.isEmpty()) {
            return List.of();
        }

        List<ModelImageDto> result = new ArrayList<>();
        for (String path : imagePaths) {
            try {
                // Get image dimensions — use createImageData to avoid SWT Image disposal
                ImageData data = archiveManager.createImageData(path);
                if (data != null) {
                    result.add(new ModelImageDto(path, data.width, data.height));
                }
            } catch (Exception e) {
                // Skip images that can't be loaded — don't fail the whole list
                logger.warn("Could not load image dimensions for path: {}", path, e);
                result.add(new ModelImageDto(path, 0, 0));
            }
        }

        logger.info("Found {} images in model archive", result.size());
        return result;
    }
}
