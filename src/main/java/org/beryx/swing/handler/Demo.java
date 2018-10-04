/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.beryx.swing.handler;

import org.beryx.textio.TextIO;
import org.beryx.textio.swing.SwingTextTerminal;

public class Demo {
    private static class Product {
        public String name;
        public int quantity;
        public Double unitPrice;
        public String color;

        @Override
        public String toString() {
            return "\n\tproduct name: " + name +
                    "\n\tquantity: " + quantity +
                    "\n\tunit price: " + unitPrice +
                    "\n\tcolor: " + color;
        }
    }


    public static void main(String[] args) {
        SwingTextTerminal terminal = new SwingTextTerminal();
        terminal.init();
        TextIO textIO = new TextIO(terminal);

        Product product = new Product();
        SwingHandler handler = new SwingHandler(textIO, product);

        terminal.println("----------------------------------------------------------------");
        terminal.println("|   Use the up and down arrow keys to scroll through choices.  |");
        terminal.println("|   Press '" + handler.getBackKeyStroke() + "' to go back to the previous field.           |");
        terminal.println("----------------------------------------------------------------\n");

        handler.addStringTask("name", "Product name")
                .addChoices("albert", "alice", "ava", "betty", "cathy");
        handler.addIntTask("quantity", "Quantity")
                .addChoices(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
        handler.addDoubleTask("unitPrice", "Unit price")
                .addChoices(0.59, 0.86, 0.99, 1.14, 1.55, 1.63, 1.74, 1.99, 2.55, 2.88, 2.99);
        handler.addStringTask("color", "Color")
                .addChoices("black", "blue", "green", "pink", "purple", "red", "yellow", "white");
        handler.execute();

        terminal.println("\nProduct info: " + product);

        textIO.newStringInputReader().withMinLength(0).read("\nPress enter to terminate...");
        textIO.dispose();
    }
}
