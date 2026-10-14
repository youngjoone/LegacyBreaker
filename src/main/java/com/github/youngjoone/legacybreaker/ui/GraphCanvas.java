package com.github.youngjoone.legacybreaker.ui;

import com.github.youngjoone.legacybreaker.model.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.CubicCurve2D;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Small dependency-free directed graph. No PSI reads, searches or layout threads here. */
public final class GraphCanvas extends JComponent {
    private static final int WIDTH = 270, HEIGHT = 68, COLUMN = 340, ROW = 104;
    private final CodeGraph graph;
    private final Consumer<String> select;
    private final Runnable navigate;
    private final Consumer<MouseEvent> popup;
    private final Map<String, Point> positions = new LinkedHashMap<>();
    private String selected;
    private long resetVersion = -1;
    private int originX = 40, originY = 50;
    private Point dragStart;
    private Point nodeStart;
    private String dragging;
    private boolean moved;
    private String emptyMessage = "Explore a Java method from the editor context menu.";

    public GraphCanvas(CodeGraph graph, Consumer<String> select, Runnable navigate, Consumer<MouseEvent> popup) {
        this.graph = graph;
        this.select = select;
        this.navigate = navigate;
        this.popup = popup;
        setOpaque(true);
        setFocusable(true);
        setToolTipText("");
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
                String id = hit(event.getPoint());
                if (id != null) select.accept(id);
                if (event.isPopupTrigger()) popup.accept(event);
                if (SwingUtilities.isLeftMouseButton(event) && id != null) {
                    dragging = id;
                    dragStart = event.getPoint();
                    nodeStart = new Point(positions.get(id));
                    moved = false;
                }
            }
            @Override public void mouseReleased(MouseEvent event) {
                if (event.isPopupTrigger()) popup.accept(event);
                if (dragging != null) updateBounds(false);
                dragging = null;
            }
            @Override public void mouseDragged(MouseEvent event) {
                if (dragging == null) return;
                int dx = event.getX() - dragStart.x, dy = event.getY() - dragStart.y;
                if (Math.abs(dx) + Math.abs(dy) > 4) moved = true;
                positions.put(dragging, new Point(nodeStart.x + dx, nodeStart.y + dy));
                repaint();
            }
            @Override public void mouseClicked(MouseEvent event) {
                if (!moved && SwingUtilities.isLeftMouseButton(event) && event.getClickCount() == 2
                        && hit(event.getPoint()) != null) navigate.run();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "source");
        getActionMap().put("source", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { navigate.run(); }
        });
    }

    public void sync(String selectedId) {
        selected = selectedId;
        boolean reset = resetVersion != graph.resetVersion();
        if (reset) { positions.clear(); resetVersion = graph.resetVersion(); }
        if (graph.rootId() != null && !positions.containsKey(graph.rootId())) positions.put(graph.rootId(), new Point());
        for (CodeNode node : graph.nodes()) {
            if (positions.containsKey(node.id)) continue;
            int x = COLUMN;
            for (CodeEdge edge : graph.edges()) {
                if (edge.target.equals(node.id) && positions.containsKey(edge.source)) {
                    x = positions.get(edge.source).x + COLUMN;
                    break;
                }
                if (edge.source.equals(node.id) && positions.containsKey(edge.target)) {
                    x = positions.get(edge.target).x - COLUMN;
                    break;
                }
            }
            int y = 0;
            for (Point point : positions.values()) {
                if (Math.abs(point.x - x) < WIDTH + 20) y = Math.max(y, point.y + ROW);
            }
            positions.put(node.id, new Point(x, y));
        }
        updateBounds(reset);
        repaint();
    }

    private void updateBounds(boolean reset) {
        int minX = 0, minY = 0, maxX = WIDTH, maxY = HEIGHT;
        for (Point point : positions.values()) {
            minX = Math.min(minX, point.x); minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x + WIDTH); maxY = Math.max(maxY, point.y + HEIGHT);
        }
        int dx = 40 - minX - originX, dy = 50 - minY - originY;
        originX = 40 - minX; originY = 50 - minY;
        Dimension size = new Dimension(Math.max(700, maxX - minX + 100), Math.max(350, maxY - minY + 100));
        if (!size.equals(getPreferredSize())) { setPreferredSize(size); revalidate(); }
        if (getParent() instanceof JViewport) {
            JViewport viewport = (JViewport) getParent();
            Point current = viewport.getViewPosition();
            if (reset) SwingUtilities.invokeLater(this::centerRoot);
            else if (dx != 0 || dy != 0) {
                viewport.setViewPosition(new Point(Math.max(0, current.x + dx), Math.max(0, current.y + dy)));
            }
        }
    }

    public void centerRoot() {
        Point point = positions.get(graph.rootId());
        if (point != null) scrollRectToVisible(new Rectangle(point.x + originX - 20,
                point.y + originY - 20, WIDTH + 40, HEIGHT + 40));
    }

    public void setEmptyMessage(String message) { emptyMessage = message; }

    private String hit(Point point) {
        for (Map.Entry<String, Point> entry : positions.entrySet()) {
            Point p = entry.getValue();
            if (new Rectangle(p.x + originX, p.y + originY, WIDTH, HEIGHT).contains(point)) return entry.getKey();
        }
        return null;
    }

    @Override public String getToolTipText(MouseEvent event) {
        CodeNode node = graph.node(hit(event.getPoint()));
        return node == null ? null : node.type + ": " + node.className + "#" + node.name + " | " + node.filePath
                + (node.line > 0 ? ":" + node.line : "") + (node.external ? " [library: terminal node]" : "");
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            Color background = UIManager.getColor("Panel.background");
            Color foreground = UIManager.getColor("Label.foreground");
            if (background == null) background = Color.WHITE;
            if (foreground == null) foreground = Color.BLACK;
            g.setColor(background); g.fillRect(0, 0, getWidth(), getHeight());
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(UIManager.getFont("Label.font"));
            if (positions.isEmpty()) {
                g.setColor(foreground);
                g.drawString(emptyMessage, 30, 45);
                return;
            }
            g.translate(originX, originY);
            g.setColor(new Color(120, 135, 155));
            g.setStroke(new BasicStroke(1.5f));
            for (CodeEdge edge : graph.edges()) paintEdge(g, edge);
            for (CodeNode node : graph.nodes()) {
                Point p = positions.get(node.id);
                boolean root = node.id.equals(graph.rootId());
                boolean active = node.id.equals(selected);
                g.setColor(background);
                g.fillRoundRect(p.x, p.y, WIDTH, HEIGHT, 14, 14);
                g.setColor(active ? new Color(70, 150, 235) : root ? new Color(90, 170, 120) : foreground);
                g.setStroke(new BasicStroke(active || root ? 2.5f : 1f));
                g.drawRoundRect(p.x, p.y, WIDTH, HEIGHT, 14, 14);
                g.setColor(foreground);
                text(g, (root ? "[Root] " : "") + node.name, p.x + 10, p.y + 24);
                text(g, node.className + (node.external ? " [library]" : ""), p.x + 10, p.y + 47);
            }
        } finally { g.dispose(); }
    }

    private static void text(Graphics2D g, String value, int x, int y) {
        FontMetrics metrics = g.getFontMetrics();
        String clipped = value;
        while (clipped.length() > 1 && metrics.stringWidth(clipped) > WIDTH - 28) {
            clipped = clipped.substring(0, clipped.length() - 1);
        }
        g.drawString(clipped.equals(value) ? value : clipped + "…", x, y);
    }

    private void paintEdge(Graphics2D g, CodeEdge edge) {
        boolean candidate = edge.type.startsWith("CANDIDATE");
        g.setColor(candidate ? new Color(180, 130, 60) : new Color(120, 135, 155));
        g.setStroke(candidate ? new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10, new float[]{5, 4}, 0)
                : new BasicStroke(1.5f));
        Point from = positions.get(edge.source), to = positions.get(edge.target);
        if (from == null || to == null) return;
        if (edge.source.equals(edge.target)) {
            double x = from.x + WIDTH - 50, y = from.y;
            g.draw(new CubicCurve2D.Double(x, y, x - 35, y - 50, x + 75, y - 50, x + 30, y));
            arrow(g, x + 30, y, -Math.PI / 4 + Math.PI / 2);
            return;
        }
        boolean right = to.x >= from.x;
        double x1 = from.x + (right ? WIDTH : 0), y1 = from.y + HEIGHT / 2.0;
        double x2 = to.x + (right ? 0 : WIDTH), y2 = to.y + HEIGHT / 2.0;
        double bend = Math.max(45, Math.abs(x2 - x1) / 2);
        double c1 = x1 + (right ? bend : -bend), c2 = x2 + (right ? -bend : bend);
        g.draw(new CubicCurve2D.Double(x1, y1, c1, y1, c2, y2, x2, y2));
        arrow(g, x2, y2, right ? 0 : Math.PI);
        if (!CodeEdge.CALLS.equals(edge.type)) {
            //4차수정: 변수 관계는 읽기/쓰기/초기값 간선을 구분하여 표시한다.
            String label = candidate ? "candidate" : edge.type.equals("OUTPUTS") ? "output"
                    : edge.type.equals("READS") ? "read" : edge.type.equals("WRITES") ? "write"
                    : edge.type.equals("INITIALIZES") ? "init" : "value";
            g.drawString(label, (int) ((x1 + x2) / 2 - g.getFontMetrics().stringWidth(label) / 2), (int) ((y1 + y2) / 2 - 8));
        }
    }

    private static void arrow(Graphics2D g, double x, double y, double angle) {
        for (double side : new double[]{-0.48, 0.48}) {
            g.drawLine((int) x, (int) y, (int) (x - 10 * Math.cos(angle + side)),
                    (int) (y - 10 * Math.sin(angle + side)));
        }
    }
}
