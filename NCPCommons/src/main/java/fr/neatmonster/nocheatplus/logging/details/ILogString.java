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

import java.util.logging.Level;

/**
 * Standard logging for String messages.
 * 
 * @author asofold
 *
 */
public interface ILogString {

    void debug(StreamID streamID, String message);

    void info(StreamID streamID, String message);

    void warning(StreamID streamID, String message);

    void severe(StreamID streamID, String message);

    void log(StreamID streamID, Level level, String message);

}
