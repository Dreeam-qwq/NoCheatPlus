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
package fr.neatmonster.nocheatplus.components.data;

import fr.neatmonster.nocheatplus.components.registry.IGetGenericInstance;

/**
 * Configuration reload: Data storage specific listener for explicit
 * registration with the appropriate registry.
 * 
 * @author asofold
 *
 */
public interface IDataOnReload {

    /**
     * Called after the configuration has been reloaded.
     * 
     * @param dataAccess
     *            Applicable data access point for data / configs.
     * @return Return true to remove the data instance from the cache in
     *         question, false otherwise.
     */
    boolean dataOnReload(IGetGenericInstance dataAccess);

}
