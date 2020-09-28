package org.apache.jackrabbit.oak.explorer;

import com.google.common.base.Function;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import org.apache.jackrabbit.oak.api.Blob;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.segment.RecordId;
import org.apache.jackrabbit.oak.segment.SegmentBlob;
import org.apache.jackrabbit.oak.segment.SegmentId;
import org.apache.jackrabbit.oak.segment.SegmentNodeState;
import org.apache.jackrabbit.oak.segment.SegmentNodeStateHelper;
import org.apache.jackrabbit.oak.segment.SegmentPropertyState;
import org.apache.jackrabbit.oak.segment.file.JournalEntry;
import org.apache.jackrabbit.oak.segment.file.JournalReader;
import org.apache.jackrabbit.oak.segment.file.ReadOnlyFileStore;
import org.apache.jackrabbit.oak.segment.spi.persistence.JournalFile;
import org.apache.jackrabbit.oak.spi.state.NodeState;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static java.util.Collections.reverseOrder;

/**
 * Abstraction for Segment-Tar based backends.
 */
public abstract class AbstractSegmentTarExplorerBackend implements ExplorerBackend {
    protected ReadOnlyFileStore store;
    protected Map<String, Set<UUID>> index;


    public abstract void open() throws IOException;

    public void close() {
        store.close();
        store = null;
        index = null;
    }

    abstract protected JournalFile getJournal();

    public List<String> readRevisions() {
        JournalFile journal = getJournal();

        if (!journal.exists()) {
            return newArrayList();
        }

        List<String> revs = newArrayList();
        JournalReader journalReader = null;

        try {
            journalReader = new JournalReader(journal);
            Iterator<String> revisionIterator = Iterators.transform(journalReader,
                    new Function<JournalEntry, String>() {
                        @Override
                        public String apply(JournalEntry entry) {
                            return entry.getRevision();
                        }
                    });

            try {
                revs = newArrayList(revisionIterator);
            } finally {
                journalReader.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (journalReader != null) {
                    journalReader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return revs;
    }

    public Map<String, Set<UUID>> getTarReaderIndex() {
        return store.getTarReaderIndex();
    }

    public Map<UUID, Set<UUID>> getTarGraph(String file) throws IOException {
        return store.getTarGraph(file);
    }

    public List<String> getTarFiles() {
        List<String> files = new ArrayList<>(store.getTarReaderIndex().keySet());
        files.sort(reverseOrder());
        return files;
    }

    public void getGcRoots(UUID uuidIn, Map<UUID, Set<Map.Entry<UUID, String>>> links) throws IOException {
        Deque<UUID> todos = new ArrayDeque<UUID>();
        todos.add(uuidIn);
        Set<UUID> visited = newHashSet();
        while (!todos.isEmpty()) {
            UUID uuid = todos.remove();
            if (!visited.add(uuid)) {
                continue;
            }
            for (String f : getTarFiles()) {
                Map<UUID, Set<UUID>> graph = store.getTarGraph(f);
                for (Map.Entry<UUID, Set<UUID>> g : graph.entrySet()) {
                    if (g.getValue() != null && g.getValue().contains(uuid)) {
                        UUID uuidP = g.getKey();
                        if (!todos.contains(uuidP)) {
                            todos.add(uuidP);
                            Set<Map.Entry<UUID, String>> deps = links.get(uuid);
                            if (deps == null) {
                                deps = newHashSet();
                                links.put(uuid, deps);
                            }
                            deps.add(new AbstractMap.SimpleImmutableEntry<UUID, String>(
                                    uuidP, f));
                        }
                    }
                }
            }
        }
    }

    public Set<UUID> getReferencedSegmentIds() {
        Set<UUID> ids = newHashSet();

        for (SegmentId id : store.getReferencedSegmentIds()) {
            ids.add(id.asUUID());
        }

        return ids;
    }

    public NodeState getHead() {
        return store.getHead();
    }

    public NodeState readNodeState(String recordId) {
        return store.getReader().readNode(RecordId.fromString(store.getSegmentIdProvider(), recordId));
    }

    public void setRevision(String revision) {
        store.setRevision(revision);
    }

    public boolean isPersisted(NodeState state) {
        return state instanceof SegmentNodeState;
    }

    public boolean isPersisted(PropertyState state) {
        return state instanceof SegmentPropertyState;
    }

    public String getRecordId(NodeState state) {
        if (state instanceof SegmentNodeState) {
            return getRecordId((SegmentNodeState) state);
        }

        return null;
    }

    public UUID getSegmentId(NodeState state) {
        if (state instanceof SegmentNodeState) {
            return getSegmentId((SegmentNodeState) state);
        }

        return null;
    }

    public String getRecordId(PropertyState state) {
        if (state instanceof SegmentPropertyState) {
            return getRecordId((SegmentPropertyState) state);
        }

        return null;
    }

    public UUID getSegmentId(PropertyState state) {
        if (state instanceof SegmentPropertyState) {
            return getSegmentId((SegmentPropertyState) state);
        }

        return null;
    }

    public String getTemplateRecordId(NodeState state) {
        if (state instanceof SegmentNodeState) {
            return getTemplateRecordId((SegmentNodeState) state);
        }

        return null;
    }

    public UUID getTemplateSegmentId(NodeState state) {
        if (state instanceof SegmentNodeState) {
            return getTemplateSegmentId((SegmentNodeState) state);
        }

        return null;
    }

    public String getFile(NodeState state) {
        if (state instanceof SegmentNodeState) {
            return getFile((SegmentNodeState) state);
        }

        return null;
    }

    public String getFile(PropertyState state) {
        if (state instanceof SegmentPropertyState) {
            return getFile((SegmentPropertyState) state);
        }

        return null;
    }

    public String getTemplateFile(NodeState state) {
        if (state instanceof SegmentNodeState) {
            return getTemplateFile((SegmentNodeState) state);
        }

        return null;
    }

    public Map<UUID, String> getBulkSegmentIds(Blob blob) {
        Map<UUID, String> result = Maps.newHashMap();

        for (SegmentId segmentId : SegmentBlob.getBulkSegmentIds(blob)) {
            result.put(segmentId.asUUID(), getFile(segmentId));
        }

        return result;
    }

    public String getPersistedCompactionMapStats() {
        return "";
    }

    public boolean isExternal(Blob blob) {
        if (blob instanceof SegmentBlob) {
            return isExternal((SegmentBlob) blob);
        }

        return false;
    }

    private boolean isExternal(SegmentBlob blob) {
        return blob.isExternal();
    }

    private String getRecordId(SegmentNodeState state) {
        return state.getRecordId().toString();
    }

    private UUID getSegmentId(SegmentNodeState state) {
        return state.getRecordId().getSegmentId().asUUID();
    }

    private String getRecordId(SegmentPropertyState state) {
        return state.getRecordId().toString();
    }

    private UUID getSegmentId(SegmentPropertyState state) {
        return state.getRecordId().getSegmentId().asUUID();
    }

    private String getTemplateRecordId(SegmentNodeState state) {
        RecordId recordId = SegmentNodeStateHelper.getTemplateId(state);

        if (recordId == null) {
            return null;
        }

        return recordId.toString();
    }

    private UUID getTemplateSegmentId(SegmentNodeState state) {
        RecordId recordId = SegmentNodeStateHelper.getTemplateId(state);

        if (recordId == null) {
            return null;
        }

        return recordId.getSegmentId().asUUID();
    }

    private String getFile(SegmentNodeState state) {
        return getFile(state.getRecordId().getSegmentId());
    }

    private String getFile(SegmentPropertyState state) {
        return getFile(state.getRecordId().getSegmentId());
    }

    private String getTemplateFile(SegmentNodeState state) {
        RecordId recordId = SegmentNodeStateHelper.getTemplateId(state);

        if (recordId == null) {
            return null;
        }

        return getFile(recordId.getSegmentId());
    }

    private String getFile(SegmentId segmentId) {
        for (Map.Entry<String, Set<UUID>> nameToId : index.entrySet()) {
            for (UUID uuid : nameToId.getValue()) {
                if (uuid.equals(segmentId.asUUID())) {
                    return nameToId.getKey();
                }
            }
        }
        return null;
    }
}
