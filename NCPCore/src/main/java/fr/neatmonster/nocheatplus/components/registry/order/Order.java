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
package fr.neatmonster.nocheatplus.components.registry.order;

import java.util.Comparator;

/**
 * Utilities for sorting out order. <br>
 * TODO: DEPRECATE and later remove.
 * 
 * @author asofold
 *
 */
public class Order {

    /**
     * Comparator for sorting SetupOrder.
     */
    public static Comparator<Object> cmpSetupOrder = (obj1, obj2) -> {
        int prio1 = 0;
        int prio2 = 0;
        final SetupOrder order1 = obj1.getClass().getAnnotation(SetupOrder.class);
        if (order1 != null) {
            prio1 = order1.priority();
        }
        final SetupOrder order2 = obj2.getClass().getAnnotation(SetupOrder.class);
        if (order2 != null) {
            prio2 = order2.priority();
        }
        return Integer.compare(prio1, prio2);
    };

}
