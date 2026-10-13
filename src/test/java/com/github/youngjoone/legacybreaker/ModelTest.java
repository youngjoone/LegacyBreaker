package com.github.youngjoone.legacybreaker;

import com.github.youngjoone.legacybreaker.model.*;
import com.github.youngjoone.legacybreaker.ui.GraphCanvas;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;

/** Plain Java assertions: no third-party test dependency required. */
public final class ModelTest {
    private static CodeNode node(String id) { return new CodeNode(id, "METHOD", id + "()", "Example", "Example.java", 1, false); }
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }

    public static void main(String[] args) throws Exception {
        CodeGraph graph = new CodeGraph();
        graph.reset(node("root"));
        graph.connect("root", node("caller"), Direction.CALLERS);
        graph.connect("root", node("callee"), Direction.CALLEES);
        graph.connect("root", node("callee"), Direction.CALLEES);
        graph.connect("root", node("root"), Direction.CALLEES);
        graph.connect("callee", node("root"), Direction.CALLEES);
        check(graph.nodes().size() == 3 && graph.edges().size() == 4);
        check(graph.edges().contains(new CodeEdge("caller", "root", CodeEdge.CALLS)));
        check(graph.edges().contains(new CodeEdge("root", "callee", CodeEdge.CALLS)));
        check(graph.edges().contains(new CodeEdge("root", "root", CodeEdge.CALLS)));
        check(!new CodeEdge("root", "callee", "READS").equals(new CodeEdge("root", "callee", CodeEdge.CALLS)));
        SwingUtilities.invokeAndWait(() -> {
            GraphCanvas canvas = new GraphCanvas(graph, id -> {}, () -> {}, event -> {});
            canvas.sync("root");
            canvas.setSize(canvas.getPreferredSize());
            BufferedImage picture = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = picture.createGraphics();
            canvas.paint(g); g.dispose();
            try { javax.imageio.ImageIO.write(picture, "png", new java.io.File("build/graph-preview.png")); }
            catch (java.io.IOException e) { throw new RuntimeException(e); }
        });
        for (int i = 0; i < 1000; i++) graph.connect("root", node("node" + i), Direction.CALLEES);
        check(graph.nodes().size() == CodeGraph.MAX_NODES);
        for (CodeEdge edge : graph.edges()) check(graph.node(edge.source) != null && graph.node(edge.target) != null);
        for (CodeNode from : graph.nodes()) {
            for (CodeNode to : graph.nodes()) graph.connect(from.id, to, Direction.CALLEES);
        }
        check(graph.edges().size() == CodeGraph.MAX_EDGES);
        long version = graph.resetVersion();
        graph.reset(node("next"));
        check(graph.nodes().size() == 1 && graph.edges().isEmpty() && graph.resetVersion() > version);
        System.out.println("PASS: direction, deduplication, recursion, cycles, limits, reset, Swing render");
    }
}
