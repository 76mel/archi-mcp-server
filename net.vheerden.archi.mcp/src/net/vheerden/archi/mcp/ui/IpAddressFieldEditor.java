package net.vheerden.archi.mcp.ui;

import java.util.regex.Pattern;

import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

/**
 * A field editor for IP address input with validation.
 * Accepts valid IPv4 addresses, {@code localhost}, and {@code 0.0.0.0}.
 */
public class IpAddressFieldEditor extends StringFieldEditor {

    /** Pattern for validating IPv4 addresses and localhost. Package-private for testing. */
    static final Pattern IP_PATTERN = Pattern.compile(
            "^(localhost|([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\."
            + "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\."
            + "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\."
            + "([01]?\\d\\d?|2[0-4]\\d|25[0-5]))$");

    /**
     * Validates an IP address string against the pattern.
     * This method is provided for testability without SWT widgets.
     *
     * @param address the address to validate
     * @return true if the address is valid IPv4 or localhost
     */
    public static boolean isValidAddress(String address) {
        return address != null && IP_PATTERN.matcher(address).matches();
    }

    /**
     * Creates an IP address field editor.
     *
     * @param name      the preference key
     * @param labelText the label text displayed next to the field
     * @param parent    the parent composite
     */
    public IpAddressFieldEditor(String name, String labelText, Composite parent) {
        super(name, labelText, parent);
        setEmptyStringAllowed(false);
        setErrorMessage("Invalid bind address. Use IPv4 format (e.g., 127.0.0.1) or 'localhost'");
    }

    @Override
    protected boolean doCheckState() {
        return IP_PATTERN.matcher(getTextControl().getText()).matches();
    }

    /**
     * Exposes the underlying text control so the preference page can attach an exposure-warning
     * {@link org.eclipse.jface.fieldassist.ControlDecoration} to the bind-address field. The text
     * control is filled in by the three-arg ({@code parent}) constructor, so this is non-null once
     * the editor has been constructed with a parent composite (callers should still null-check).
     *
     * @return the bind-address text control, or null if no parent composite was supplied
     */
    public Text getBindTextControl() {
        return getTextControl();
    }
}
