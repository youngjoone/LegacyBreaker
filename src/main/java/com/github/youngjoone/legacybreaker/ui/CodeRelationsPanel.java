package com.github.youngjoone.legacybreaker.ui;

import com.github.youngjoone.legacybreaker.model.*;
import com.github.youngjoone.legacybreaker.session.ExplorationSessionService;
import com.intellij.openapi.Disposable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public final class CodeRelationsPanel extends JPanel implements Disposable {
    private final ExplorationSessionService session;
    private final GraphCanvas canvas;
    private final JButton callers = new JButton("Expand Callers");
    private final JButton callees = new JButton("Expand Callees");
    private final JButton source = new JButton("Go To Source");
    private final JButton refresh = new JButton("Refresh Root");
    private final JButton cancel = new JButton("Cancel Search");
    private final JTextArea details = new JTextArea(3, 50);
    private final JTextArea status = new JTextArea(2, 50);
    private final Runnable listener = this::update;

    public CodeRelationsPanel(ExplorationSessionService session) {
        super(new BorderLayout(0, 6));
        this.session = session;
        canvas = new GraphCanvas(session.graph(), session::select, session::navigateSelected, this::popup);
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        callers.addActionListener(e -> session.expand(session.selectedId(), Direction.CALLERS));
        callees.addActionListener(e -> session.expand(session.selectedId(), Direction.CALLEES));
        source.addActionListener(e -> session.navigateSelected());
        refresh.setToolTipText("Rebuild the graph from the current root; clears existing expansions.");
        refresh.addActionListener(e -> session.refresh());
        cancel.addActionListener(e -> session.cancel());
        JButton center = new JButton("Show Root");
        center.addActionListener(e -> canvas.centerRoot());
        for (JButton button : new JButton[]{callers, callees, source, refresh, cancel, center}) toolbar.add(button);
        add(toolbar, BorderLayout.NORTH);
        add(new JScrollPane(canvas), BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(0, 4));
        for (JTextArea area : new JTextArea[]{details, status}) {
            area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true);
            area.setOpaque(false); area.setFont(UIManager.getFont("Label.font"));
        }
        footer.setBorder(BorderFactory.createEmptyBorder(3, 8, 5, 8));
        footer.add(details, BorderLayout.CENTER);
        footer.add(status, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);
        session.addListener(listener);
        update();
    }

    private void update() {
        String id = session.selectedId();
        CodeNode node = session.graph().node(id);
        callers.setEnabled(session.canExpand(id, Direction.CALLERS));
        callees.setEnabled(session.canExpand(id, Direction.CALLEES));
        source.setEnabled(node != null);
        refresh.setEnabled(session.graph().rootId() != null);
        cancel.setEnabled(session.isLoading());
        details.setText(node == null ? "Select a node. Double-click or press Enter to open its source. Drag nodes to arrange."
                : node.className + "#" + node.name + (node.external ? " [library: no further expansion]" : "")
                + "\nCallers: " + session.directionStatus(id, Direction.CALLERS)
                + " | Callees: " + session.directionStatus(id, Direction.CALLEES));
        status.setText(session.status() + "\n" + session.graph().nodes().size() + " nodes / "
                + session.graph().edges().size() + " edges. Arrows: caller → callee."
                + (session.isLoading() ? " Searching..." : ""));
        canvas.sync(id);
    }

    private void popup(MouseEvent event) {
        if (session.graph().node(session.selectedId()) == null) return;
        JPopupMenu menu = new JPopupMenu();
        for (JButton button : new JButton[]{callers, callees, source}) {
            JMenuItem item = new JMenuItem(button.getText());
            item.setEnabled(button.isEnabled());
            item.addActionListener(e -> button.doClick());
            menu.add(item);
        }
        menu.show(canvas, event.getX(), event.getY());
    }

    @Override public void dispose() { session.removeListener(listener); }
}
