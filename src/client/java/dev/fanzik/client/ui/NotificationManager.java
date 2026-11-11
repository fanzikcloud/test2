package dev.fanzik.client.ui;

import dev.fanzik.client.render.Icons;
import dev.fanzik.client.render.Render2D;
import dev.fanzik.client.util.Argb;
import net.minecraft.text.Text;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

public final class NotificationManager {
    private static final long LIFETIME = TimeUnit.MILLISECONDS.toNanos(4500);
    private final ConcurrentLinkedDeque<Notification> notifications = new ConcurrentLinkedDeque<>();

    public void push(Text title, Text description, Kind kind) {
        notifications.addFirst(new Notification(title.copy(), description.copy(), kind, System.nanoTime()));
        while (notifications.size() > 4) {
            notifications.pollLast();
        }
    }

    public void render(Render2D r, int width, int height, int accent) {
        long now = System.nanoTime();
        float scale = Math.min(1, Math.max(0.5f, width / 420f));
        float cardWidth = 242;
        float offsetY = 0;
        Iterator<Notification> iterator = notifications.iterator();
        while (iterator.hasNext()) {
            Notification notification = iterator.next();
            long elapsed = now - notification.createdAt;
            if (elapsed > LIFETIME) {
                iterator.remove();
                continue;
            }
            double enter = Math.clamp(elapsed / (double) TimeUnit.MILLISECONDS.toNanos(220), 0, 1);
            double exit = Math.clamp((LIFETIME - elapsed) / (double) TimeUnit.MILLISECONDS.toNanos(260), 0, 1);
            double visibility = (1 - Math.pow(1 - enter, 3)) * exit;
            float x = width - (cardWidth + 12) * scale + (float) ((1 - visibility) * 36);
            float y = height - (offsetY + 68) * scale;
            try (Render2D.Scope transform = r.transform(x, y, scale); Render2D.Scope opacity = r.opacity(visibility)) {
                int color = switch (notification.kind) {
                    case INFO -> accent;
                    case SUCCESS -> Theme.SUCCESS;
                    case ERROR -> Theme.ERROR;
                };
                r.shadow(0, 0, cardWidth, 54, 10, 8, 0xA0000000);
                r.rounded(0, 0, cardWidth, 54, 10, 0xF5161B29);
                r.stroke(0, 0, cardWidth, 54, 10, 0.7f, Argb.alpha(color, 0.3));
                r.rounded(12, 13, 25, 25, 7, Argb.alpha(color, 0.12));
                if (notification.kind == Kind.ERROR) {
                    Icons.close(r, 18, 19, color);
                } else {
                    Icons.check(r, 19, 21, color);
                }
                r.text(r.elide(notification.title.getString(), 184), 47, 13, Theme.TEXT);
                r.text(r.elide(notification.description.getString(), 184), 47, 29, 0.85f, Theme.SECONDARY);
                r.rounded(10, 51, (cardWidth - 20) * (1 - elapsed / (float) LIFETIME), 1, 0.5f, Argb.alpha(color, 0.6));
            }
            offsetY += 62 * (float) visibility;
        }
    }

    public enum Kind {
        INFO, SUCCESS, ERROR
    }

    private record Notification(Text title, Text description, Kind kind, long createdAt) {
    }
}
