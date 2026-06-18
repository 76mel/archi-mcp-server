package net.vheerden.archi.mcp.ui;

import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.menus.UIElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vheerden.archi.mcp.model.ApprovalQueueListener;
import net.vheerden.archi.mcp.server.ApprovalService;
import net.vheerden.archi.mcp.server.McpServerManager;
import net.vheerden.archi.mcp.server.McpServerStateListener;
import net.vheerden.archi.mcp.server.ServerState;

/**
 * Opens/focuses the {@link PendingApprovalsView} and renders the ambient pending count in the MCP
 * Server menu as <strong>{@code Pending approvals (N)}</strong>.
 *
 * <p>The dynamic label mirrors {@link ToggleApprovalModeHandler}'s {@link IElementUpdater} pattern.
 * The count tracks the live aggregate: the handler registers as an {@link ApprovalQueueListener}
 * (re-acquiring the {@link ApprovalService} across server restarts via {@link McpServerStateListener})
 * and refreshes the menu element when the queue changes. When {@code N = 0} the item is disabled —
 * the empty view is still reachable via {@code Window → Show View}.</p>
 */
public class OpenPendingApprovalsHandler extends AbstractHandler
        implements IElementUpdater, ApprovalQueueListener, McpServerStateListener {

    private static final Logger logger = LoggerFactory.getLogger(OpenPendingApprovalsHandler.class);

    private static final String COMMAND_ID = "net.vheerden.archi.mcp.openPendingApprovals";

    /** The service we are currently registered against (swapped when the server restarts). */
    private ApprovalService registeredService;

    public OpenPendingApprovalsHandler() {
        // The manager singleton always exists; state callbacks let us re-hook the queue listener
        // when the server (and therefore the ApprovalService) starts or stops.
        McpServerManager.getInstance().addStateListener(this);
    }

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
        if (window != null && window.getActivePage() != null) {
            try {
                window.getActivePage().showView(PendingApprovalsView.VIEW_ID);
            } catch (Exception e) {
                logger.warn("Could not open Pending Approvals view: {}", e.getMessage());
            }
        }
        refresh();
        return null;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void updateElement(UIElement element, Map parameters) {
        ensureListenerRegistered();
        int count = pendingCount();
        element.setText("Pending approvals (" + count + ")");
        element.setTooltip(count > 0
                ? count + " change" + (count == 1 ? "" : "s") + " waiting for your approval — click to review."
                : "No changes are waiting for approval.");
        setBaseEnabled(count > 0);
    }

    @Override
    public void onQueueChanged() {
        refresh();
    }

    @Override
    public void onStateChanged(ServerState oldState, ServerState newState) {
        // The ApprovalService is created/dropped with the server — re-hook and refresh the count.
        refresh();
    }

    private void refresh() {
        Display display = Display.getDefault();
        if (display == null || display.isDisposed()) {
            return;
        }
        display.asyncExec(() -> {
            ensureListenerRegistered();
            setBaseEnabled(pendingCount() > 0);
            try {
                org.eclipse.ui.PlatformUI.getWorkbench()
                        .getService(org.eclipse.ui.commands.ICommandService.class)
                        .refreshElements(COMMAND_ID, null);
            } catch (Exception e) {
                // Workbench may be unavailable during early startup — label refreshes on next show.
                logger.debug("Could not refresh pending-approvals menu label: {}", e.getMessage());
            }
        });
    }

    private int pendingCount() {
        ApprovalService service = McpServerManager.getInstance().getApprovalService();
        return service != null ? service.listAllPending().size() : 0;
    }

    private void ensureListenerRegistered() {
        ApprovalService service = McpServerManager.getInstance().getApprovalService();
        if (service == registeredService) {
            return;
        }
        if (registeredService != null) {
            registeredService.removeQueueListener(this);
        }
        if (service != null) {
            service.addQueueListener(this);
        }
        registeredService = service;
    }

    @Override
    public void dispose() {
        McpServerManager.getInstance().removeStateListener(this);
        if (registeredService != null) {
            registeredService.removeQueueListener(this);
            registeredService = null;
        }
        super.dispose();
    }
}
