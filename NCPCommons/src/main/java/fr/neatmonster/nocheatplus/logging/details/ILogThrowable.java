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
package fr.neatmonster.nocheatplus.logging.details;

import fr.neatmonster.nocheatplus.logging.StreamID;
import org.apache.logging.log4j.Level;

/**
 * Standard logging for Throwable throwables.
 * 
 * @author asofold
 *
 */
public interface ILogThrowable {

    void debug(StreamID streamID, Throwable t);

    void info(StreamID streamID, Throwable t);

    void warning(StreamID streamID, Throwable t);

    void severe(StreamID streamID, Throwable t);

    void log(StreamID streamID, Level level, Throwable t);

}
