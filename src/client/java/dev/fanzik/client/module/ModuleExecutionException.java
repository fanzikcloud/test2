package dev.fanzik.client.module;

public final class ModuleExecutionException extends RuntimeException {
    private final String moduleId;

    public ModuleExecutionException(String moduleId, RuntimeException cause) {
        super("Module " + moduleId + " failed and was disabled", cause);
        this.moduleId = moduleId;
    }

    public String moduleId() {
        return moduleId;
    }
}
