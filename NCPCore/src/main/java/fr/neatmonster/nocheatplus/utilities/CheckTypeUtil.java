/*
 * This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.neatmonster.nocheatplus.utilities;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import fr.neatmonster.nocheatplus.checks.CheckType;

public class CheckTypeUtil {

    /** All descendants recursively. */
    private static final Map<CheckType, Set<CheckType>> descendantsMap = new HashMap<>();
    /** Direct children only. */
    private static final Map<CheckType, Set<CheckType>> directChildrenMap = new HashMap<>();
    /** Checks/groups that might be run off the primary thread. */
    private static final Set<CheckType> needSync = new HashSet<>();
    /** Check including all descendants recursively, for convenient iteration. */
    private static final Map<CheckType, Set<CheckType>> withDescendantsMap = new HashMap<>();

    static {
        // Parent/children relations.

        // Recursive first.
        // TODO: Really recursive (...). So create direct children first, then others from those.
        final Map<CheckType, Set<CheckType>> map = new HashMap<>();
        for (final CheckType type : CheckType.values()) {
            map.put(type, new LinkedHashSet<>());
        }
        for (final CheckType type : CheckType.values()) {
            if (type != CheckType.ALL) {
                map.get(CheckType.ALL).add(type);
            }
            for (final CheckType other : CheckType.values()) {
                if (isAncestor(other, type)) {
                    map.get(other).add(type);
                }
            }
        }
        for (final CheckType parent : map.keySet()) {
            final Set<CheckType> set = map.get(parent);
            descendantsMap.put(parent, Collections.unmodifiableSet(set));
            final Set<CheckType> wpSet = new LinkedHashSet<>(set);
            wpSet.add(parent);
            withDescendantsMap.put(parent,
                    Collections.unmodifiableSet(wpSet));
        }

        // Direct children only.
        for (final CheckType parent : map.keySet()) {
            final Set<CheckType> set = new LinkedHashSet<>(map.get(parent));
            set.removeIf(checkType -> checkType.getParent() != parent);
            directChildrenMap.put(parent, Collections.unmodifiableSet(set));
        }

        // needSync: Note that tests use the same definitions.
        for (final CheckType checkType : new CheckType[] { CheckType.CHAT,
                CheckType.NET }) {
            needSync.addAll(getWithDescendants(checkType));
        }
    }

    /**
     * Get an unmodifiable collection of descendants for the given check type.
     * Always returns a collection, does not contain check type itself.
     * 
     * @param type
     *            the check type
     * @return the descendants
     */
    public static Set<CheckType> getDescendants(
            final CheckType type) {
        return descendantsMap.get(type);
    }

    /**
     * Get an unmodifiable collection of direct children for the given check
     * type. Always returns a collection, does not contain check type itself.
     * 
     * @param type
     *            the check type
     * @return the children
     */
    public static Set<CheckType> getDirectChildren(
            final CheckType type) {
        return directChildrenMap.get(type);
    }

    /**
     * Get an unmodifiable collection of the given check type with descendants.
     * Always returns a collection, does contain the check type itself.
     * 
     * @param type
     *            the check type
     * @return the given check type with descendants
     */
    public static Set<CheckType> getWithDescendants(
            final CheckType type) {
        return withDescendantsMap.get(type);
    }

    /**
     * Check if the supposed ancestor is an ancestor of the supposed descendant.
     * Equality doesn't match here.
     * 
     * @param supposedAncestor
     *            the supposed parent
     * @param supposedDescendant
     *            the supposed child
     * @return true, if is parent
     */
    public static boolean isAncestor(final CheckType supposedAncestor,
                                     final CheckType supposedDescendant) {
        // TODO: Perhaps rename to isAncestor !?
        if (supposedAncestor == supposedDescendant) {
            return false;
        } else if (supposedAncestor == CheckType.ALL) {
            return true;
        }
        CheckType parent = supposedDescendant.getParent();
        while (parent != null)
            if (parent == supposedAncestor) {
                return true;
            } else {
                parent = parent.getParent();
            }
        return false;
    }

    /**
     * Test if the check type requires synchronization. This indicates, if a
     * check can be called off primary thread at all.
     * <hr>
     * That should be CHAT and NET checks, currently.
     * 
     * @param type
     *            the check type
     * @return true, if successful
     */
    public static boolean needsSynchronization(final CheckType type) {
        return needSync.contains(type);
    }

}
