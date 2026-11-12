package com.framebucket.subscription;

/**
 * Plan features. A plan's entitlements JSON lists the codes it includes; the backend checks them
 * before the matching actions and the frontend uses them to lock tabs and show upgrade prompts.
 */
public enum Feature {
    CRM("Enquiries, customers, events and calendar"),
    QUOTATIONS("Online quotation builder and PDF quotes"),
    TEAM("Team members and freelancers"),
    GALLERY("Online galleries and album photo selection"),
    BILLING("Payments, receipts, UPI QR codes and expenses"),
    REMINDERS("Automatic day-before event reminders"),
    PORTFOLIO("Public photography portfolio page"),
    E_ALBUM("E-album flipbooks"),
    WHATSAPP_AUTOMATION("WhatsApp notifications to clients and team"),
    FACE_RECOGNITION("AI face recognition: guests get their photos on WhatsApp"),
    PAID_DOWNLOADS("Charge customers for photo downloads"),
    CUSTOM_BRANDING("Your branding only (no \"Powered by FrameBucket\")"),
    PRIORITY_SUPPORT("Priority support");

    private final String label;

    Feature(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Lenient parse for values stored in the database. */
    public static Feature parse(String value) {
        try {
            return Feature.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
