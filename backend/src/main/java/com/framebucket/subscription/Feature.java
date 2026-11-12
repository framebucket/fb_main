package com.framebucket.subscription;

/**
 * Plan-gated capabilities. Plans store these codes in fb_user_subscription.entitlements; labels are
 * what the pricing page and admin portal show.
 */
public enum Feature {
    CRM("Enquiries, customers, events and calendar"),
    QUOTATIONS("Online quotation builder and PDF quotes"),
    CUSTOM_PACKAGES("Your own packages, services and add-ons in quotes"),
    LEAD_CAPTURE("Enquiry form for your website, Instagram and WhatsApp"),
    TEAM("Team members and freelancers"),
    GALLERY("Online galleries and album photo selection"),
    DELIVERY_TRACKER("Delivery tracker your clients can follow"),
    BILLING("Payments, receipts, UPI QR codes and expenses"),
    CONTRACTS("E-signed contracts, advance collection and quote follow-ups"),
    REMINDERS("Automatic day-before event reminders"),
    PORTFOLIO("Public photography portfolio page"),
    E_ALBUM("E-album flipbooks"),
    ALBUM_PROOFING("Album design proofing with client comments"),
    FILMS("Film and video delivery (teasers, highlights, full films)"),
    WATERMARK("Watermarked gallery previews"),
    CREW_PAYOUTS("Crew scheduling, clash alerts and payouts"),
    GEAR_COSTS("Gear checklists, travel costs and profit per event"),
    WHATSAPP_AUTOMATION("WhatsApp notifications to clients and team"),
    FACE_RECOGNITION("AI face recognition: guests get their photos on WhatsApp"),
    PAID_DOWNLOADS("Charge customers for photo downloads"),
    CUSTOM_BRANDING("Your logo and colours, no \"Powered by FrameBucket\""),
    INSIGHTS("Business insights dashboard"),
    CUSTOM_DOMAIN("Your own domain for portfolio and galleries"),
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
