package com.github.youngjoone.legacybreaker.jsp;

import com.github.youngjoone.legacybreaker.model.CodeNode;
import com.github.youngjoone.legacybreaker.ui.GraphCanvas;
import com.intellij.openapi.Disposable;
import javax.swing.*;
import java.awt.*;

public final class JspTracePanel extends JPanel implements Disposable {
    private final JspTraceService session;
    private final GraphCanvas canvas;
    private final JButton source = new JButton("Go To Source"), method = new JButton("Explore Method Calls"),
            refresh = new JButton("Refresh Trace"), cancel = new JButton("Cancel");
    private final JTextArea details = new JTextArea(5, 60);
    private final Runnable listener = this::update;

    public JspTracePanel(JspTraceService session) {
        super(new BorderLayout(0, 5)); this.session = session;
        canvas = new GraphCanvas(session.graph(), session::select, session::navigate, event -> {
            JPopupMenu menu = new JPopupMenu();
            for (JButton button : new JButton[]{source, method}) {
                JMenuItem item = new JMenuItem(button.getText()); item.setEnabled(button.isEnabled());
                item.addActionListener(e -> button.doClick()); menu.add(item);
            }
            menu.show(event.getComponent(), event.getX(), event.getY());
        });
        canvas.setEmptyMessage("Select a JSP output and choose Trace JSP Value Origin in the editor.");
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        for (JButton button : new JButton[]{source, method, refresh, cancel}) toolbar.add(button);
        source.addActionListener(e -> session.navigate()); method.addActionListener(e -> session.exploreMethod());
        refresh.addActionListener(e -> session.refresh()); cancel.addActionListener(e -> session.cancel());
        JButton root = new JButton("Show Root"); root.addActionListener(e -> canvas.centerRoot()); toolbar.add(root);
        add(toolbar, BorderLayout.NORTH); add(new JScrollPane(canvas), BorderLayout.CENTER);
        details.setEditable(false); details.setLineWrap(true); details.setWrapStyleWord(true);
        details.setOpaque(false); details.setFont(UIManager.getFont("Label.font"));
        details.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(new JScrollPane(details), BorderLayout.SOUTH);
        session.addListener(listener); update();
    }
    private void update() {
        CodeNode node = session.graph().node(session.selectedId());
        source.setEnabled(node != null); method.setEnabled(session.canExploreMethod());
        refresh.setEnabled(session.canRefresh()); cancel.setEnabled(session.isLoading());
        details.setText((node == null ? "" : node.type + ": " + node.name + "\n" + node.description + "\n")
                + session.status() + "\nArrows: value source → consumer. Dashed links: candidates, not runtime proof.");
        canvas.sync(session.selectedId());
    }
    @Override public void dispose() { session.removeListener(listener); }
}
