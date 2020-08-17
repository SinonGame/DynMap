package org.dynmap;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.Event;
import cn.nukkit.event.HandlerList;

/**
 * Custom Nukkit event, corresponding to the receiving of a web-chat message from a web UI user
 *
 * This interface is Bukkit specific.
 */
public class DynmapWebChatEvent extends Event implements Cancellable {
    public static final String CUSTOM_TYPE = "org.dynmap.DynmapWebChatEvent";
    private static final HandlerList handlers = new HandlerList();
    private String source;
    private String name;
    private String message;
    private boolean cancelled;
    private boolean isprocessed;

    public DynmapWebChatEvent(String source, String name, String message) {
        this.source = source;
        this.name = name;
        this.message = message;
        this.cancelled = false;
        this.isprocessed = false;
    }
    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { cancelled = cancel; }

    public String getSource() { return source; }

    public String getName() { return name; }

    public String getMessage() { return message; }

    /* Processed flag is used to mark message as handled by listener - if set, dynmap will not send it using broadcastMessage() */
    public boolean isProcessed() { return isprocessed; }

    public void setProcessed() { isprocessed = true; }

    public HandlerList getHandlers() {
        return handlers;
    }
    public static HandlerList getHandlerList() {
        return handlers;
    }
}

