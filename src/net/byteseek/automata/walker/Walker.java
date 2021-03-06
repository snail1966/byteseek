/*
 * Copyright Matt Palmer 2009-2011, All rights reserved.
 * 
 * This code is licensed under a standard 3-clause BSD license:
 * 
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, 
 *    this list of conditions and the following disclaimer.
 * 
 *  * Redistributions in binary form must reproduce the above copyright notice, 
 *    this list of conditions and the following disclaimer in the documentation 
 *    and/or other materials provided with the distribution.
 * 
 *  * The names of its contributors may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 * POSSIBILITY OF SUCH DAMAGE.
 */

package net.byteseek.automata.walker;

import net.byteseek.automata.State;

/**
 * An interface for classes which walk an automata beginning at the start state
 * supplied.  It takes a {@link Action} as a parameter, which is the class
 * that observes each step of the walk.
 * <p>
 * If any Action returns false from its {@link Action#process(Step)} method,
 * then the walker should stop the walk.
 * <p>
 * Different implementations can walk the automata in different ways.  Some may
 * visit each state only once, some may visit each transition once and the order
 * of visiting may be different.
 *  
 * @author Matt Palmer
 */
public interface Walker<T> {

	/**
	 * Walks the automata beginning at the startState.  The {@link Action} 
	 * is invoked for each step of the walk.
	 * 
	 * @param startState The state to begin walking at.
	 * @param observer The class which takes action for each step of the walk.
	 */
	void walk(final State<T> startState, final Action<T> observer);

}
