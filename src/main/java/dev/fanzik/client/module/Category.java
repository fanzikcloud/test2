package dev.fanzik.client.module;

public enum Category {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    RENDER("Render"),
    PLAYER("Player"),
    MISC("Misc");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String descriptionKey() {
        return "category.fanzik." + name().toLowerCase(java.util.Locale.ROOT) + ".description";
    }
}
