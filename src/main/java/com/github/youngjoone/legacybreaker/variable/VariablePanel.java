//4차수정: 신규 파일 전체 — 변수 읽기/변경 필터와 공통 그래프를 제공한다.
package com.github.youngjoone.legacybreaker.variable;

import com.github.youngjoone.legacybreaker.model.CodeNode;
import com.github.youngjoone.legacybreaker.ui.GraphCanvas;
import com.intellij.openapi.Disposable;
import javax.swing.*;
import java.awt.*;

public final class VariablePanel extends JPanel implements Disposable {
    private final VariableSession session;
    private final GraphCanvas canvas;
    private final JButton source = new JButton("Go To Source"), refresh = new JButton("Refresh"), cancel = new JButton("Cancel");
    private final JToggleButton all = new JToggleButton("All"), reads = new JToggleButton("Reads"), changes = new JToggleButton("Changes");
    private final JTextArea details = new JTextArea(6, 60);
    private final Runnable listener = this::update;

    public VariablePanel(VariableSession session) {
        super(new BorderLayout(0, 5)); this.session = session;
        canvas = new GraphCanvas(session.graph(), session::select, session::navigate, event -> {
            JPopupMenu menu = new JPopupMenu();
            JMenuItem item = new JMenuItem("Go To Source"); item.setEnabled(source.isEnabled());
            item.addActionListener(e -> session.navigate()); menu.add(item); menu.show(event.getComponent(), event.getX(), event.getY());
        });
        canvas.setEmptyMessage("Select a Java variable name and choose Explore Variable Relations.");
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        ButtonGroup group = new ButtonGroup();
        for (JToggleButton button : new JToggleButton[]{all, reads, changes}) { group.add(button); toolbar.add(button); }
        all.addActionListener(e -> session.setFilter(VariableSession.Filter.ALL));
        reads.addActionListener(e -> session.setFilter(VariableSession.Filter.READS));
        changes.addActionListener(e -> session.setFilter(VariableSession.Filter.CHANGES));
        for (JButton button : new JButton[]{source, refresh, cancel}) toolbar.add(button);
        source.addActionListener(e -> session.navigate()); refresh.addActionListener(e -> session.refresh()); cancel.addActionListener(e -> session.cancel());
        JButton root = new JButton("Show Root"); root.addActionListener(e -> canvas.centerRoot()); toolbar.add(root);
        add(toolbar, BorderLayout.NORTH); add(new JScrollPane(canvas), BorderLayout.CENTER);
        details.setEditable(false); details.setLineWrap(true); details.setWrapStyleWord(true); details.setOpaque(false);
        details.setFont(UIManager.getFont("Label.font")); details.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(new JScrollPane(details), BorderLayout.SOUTH);
        session.addListener(listener); update();
    }
    private void update() {
        CodeNode node = session.graph().node(session.selectedId());
        all.setSelected(session.filter() == VariableSession.Filter.ALL);
        reads.setSelected(session.filter() == VariableSession.Filter.READS);
        changes.setSelected(session.filter() == VariableSession.Filter.CHANGES);
        source.setEnabled(node != null); refresh.setEnabled(session.canRefresh()); cancel.setEnabled(session.isLoading());
        details.setText((node == null ? "" : node.type + ": " + node.name + "\n" + node.description + "\n" + node.className + "\n")
                + session.status() + "\nArrows: initializer/write → variable → read. READ_WRITE belongs to both filters.");
        canvas.sync(session.selectedId());
    }
    @Override public void dispose() { session.removeListener(listener); }
}
