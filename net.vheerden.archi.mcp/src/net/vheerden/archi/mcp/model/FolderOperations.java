package net.vheerden.archi.mcp.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.emf.ecore.EObject;

import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IFolder;

import net.vheerden.archi.mcp.response.dto.FolderDto;
import net.vheerden.archi.mcp.response.dto.FolderTreeDto;

/**
 * Folder navigation, search, and DTO conversion helpers.
 *
 * <p>Extracted from ArchiModelAccessorImpl to improve cohesion.
 * Package-visible — only ArchiModelAccessorImpl should use this class.</p>
 */
final class FolderOperations {

    private FolderOperations() {}

    // ---- Read facade: folder navigation/search assembly over a captured model ----

    static List<FolderDto> getRootFolders(IArchimateModel model) {
        List<FolderDto> result = new ArrayList<>();
        for (IFolder folder : model.getFolders()) {
            result.add(convertToFolderDto(folder));
        }
        return result;
    }

    static Optional<FolderDto> getFolderById(IArchimateModel model, String id) {
        IFolder found = findFolderById(model, id);
        if (found == null) {
            return Optional.empty();
        }
        return Optional.of(convertToFolderDto(found));
    }

    static List<FolderDto> getFolderChildren(IArchimateModel model, String parentId) {
        IFolder parent = findFolderById(model, parentId);
        if (parent == null) {
            return List.of();
        }
        List<FolderDto> result = new ArrayList<>();
        for (IFolder child : parent.getFolders()) {
            result.add(convertToFolderDto(child));
        }
        return result;
    }

    static List<FolderTreeDto> getFolderTree(IArchimateModel model, String rootId, int maxDepth) {
        if (rootId != null) {
            IFolder root = findFolderById(model, rootId);
            if (root == null) {
                return List.of();
            }
            return List.of(buildFolderTree(root, maxDepth, 0));
        }
        // Full tree: all root folders
        List<FolderTreeDto> result = new ArrayList<>();
        for (IFolder folder : model.getFolders()) {
            result.add(buildFolderTree(folder, maxDepth, 0));
        }
        return result;
    }

    static List<FolderDto> searchFolders(IArchimateModel model, String nameQuery) {
        String lowerQuery = nameQuery.toLowerCase();
        List<FolderDto> result = new ArrayList<>();
        for (IFolder folder : model.getFolders()) {
            collectMatchingFolders(folder, lowerQuery, result);
        }
        return result;
    }

    static IFolder findFolderById(IArchimateModel model, String id) {
        for (IFolder root : model.getFolders()) {
            IFolder found = findFolderByIdRecursive(root, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static IFolder findFolderByIdRecursive(IFolder folder, String id) {
        if (id.equals(folder.getId())) {
            return folder;
        }
        for (IFolder child : folder.getFolders()) {
            IFolder found = findFolderByIdRecursive(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    static FolderDto convertToFolderDto(IFolder folder) {
        return new FolderDto(
                folder.getId(),
                folder.getName(),
                folder.getType().name(),
                buildFolderPath(folder),
                folder.getElements().size(),
                folder.getFolders().size());
    }

    static FolderTreeDto buildFolderTree(IFolder folder, int maxDepth, int currentDepth) {
        List<FolderTreeDto> children = null;
        if (maxDepth <= 0 || currentDepth < maxDepth) {
            if (!folder.getFolders().isEmpty()) {
                children = new ArrayList<>();
                for (IFolder child : folder.getFolders()) {
                    children.add(buildFolderTree(child, maxDepth, currentDepth + 1));
                }
            }
        }
        return new FolderTreeDto(
                folder.getId(),
                folder.getName(),
                folder.getType().name(),
                buildFolderPath(folder),
                folder.getElements().size(),
                folder.getFolders().size(),
                children);
    }

    static void collectMatchingFolders(IFolder folder, String lowerQuery, List<FolderDto> result) {
        if (folder.getName() != null && folder.getName().toLowerCase().contains(lowerQuery)) {
            result.add(convertToFolderDto(folder));
        }
        for (IFolder child : folder.getFolders()) {
            collectMatchingFolders(child, lowerQuery, result);
        }
    }

    static String buildFolderPath(IFolder folder) {
        StringBuilder path = new StringBuilder();
        buildFolderPathRecursive(folder, path);
        return path.toString();
    }

    private static void buildFolderPathRecursive(IFolder folder, StringBuilder path) {
        EObject parent = folder.eContainer();
        if (parent instanceof IFolder parentFolder) {
            buildFolderPathRecursive(parentFolder, path);
            path.append('/');
        }
        path.append(folder.getName());
    }
}
