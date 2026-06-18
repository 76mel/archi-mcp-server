package net.vheerden.archi.mcp.ui;

import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.menus.UIElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vheerden.archi.mcp.server.ApprovalMode;

/**
 * SWT command handler that lets the <strong>human</strong> toggle the approval gate.
 *
 * <p>This is the one and only writer of {@link ApprovalMode#setOn(boolean)}: the agent has
 * no MCP-callable path to flip the gate. The {@link IElementUpdater} dynamic label mirrors
 * {@code ToggleServerHandler}; the status-bar lock + pending-count badge complement it.</p>
 *
 * <p><strong>Friction asymmetry:</strong> turning approval <em>ON</em> (tightening the
 * gate) is one quiet click; turning it <em>OFF</em> (loosening it) raises a confirmation naming
 * the consequence, and only flips on confirm — Cancel leaves the gate ON.</p>
 */
public class ToggleApprovalModeHandler extends AbstractHandler implements IElementUpdater {

    private static final Logger logger = LoggerFactory.getLogger(ToggleApprovalModeHandler.class);

    private static final String COMMAND_ID = "net.vheerden.archi.mcp.toggleApprovalMode";

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        ApprovalMode mode = ApprovalMode.getInstance();

        if (mode.isOn()) {
            // Turning OFF — friction: confirm and name the consequence.
            boolean confirmed = MessageDialog.openConfirm(
                    HandlerUtil.getActiveShell(event),
                    "Disable approval mode?",
                    "Allow the agent to apply changes directly, without approval? "
                            + "You can re-enable anytime.");
            if (!confirmed) {
                return null; // Cancel leaves it ON.
            }
            mode.setOn(false);
        } else {
            // Turning ON — one quiet click, no confirm.
            mode.setOn(true);
        }

        refreshLabel();
        return null;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void updateElement(UIElement element, Map parameters) {
        if (ApprovalMode.getInstance().isOn()) {
            element.setText("Approval Mode: ON (agent changes need your approval)");
            element.setTooltip("Approval mode is ON — the agent's changes wait for your approval.\n"
                    + "Click to allow direct changes (you'll be asked to confirm).");
        } else {
            element.setText("Approval Mode: OFF (agent changes apply directly)");
            element.setTooltip("Approval mode is OFF — the agent's changes apply immediately.\n"
                    + "Click to require your approval.");
        }
    }

    private void refreshLabel() {
        try {
            org.eclipse.ui.PlatformUI.getWorkbench()
                    .getService(org.eclipse.ui.commands.ICommandService.class)
                    .refreshElements(COMMAND_ID, null);
        } catch (Exception e) {
            // Workbench may be unavailable during early startup — label refreshes on next show.
            logger.debug("Could not refresh approval-mode menu label: {}", e.getMessage());
        }
    }
}
