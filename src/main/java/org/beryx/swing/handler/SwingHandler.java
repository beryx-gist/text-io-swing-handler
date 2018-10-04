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

import org.beryx.textio.ReadAbortedException;
import org.beryx.textio.ReadHandlerData;
import org.beryx.textio.ReadInterruptionStrategy;
import org.beryx.textio.TextIO;
import org.beryx.textio.swing.SwingTextTerminal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.beryx.textio.ReadInterruptionStrategy.Action.ABORT;

public class SwingHandler {
    private static final String KEY_STROKE_UP = "pressed UP";
    private static final String KEY_STROKE_DOWN = "pressed DOWN";

    private final SwingTextTerminal terminal;

    private final String backKeyStroke;

    private String originalInput = "";
    private int choiceIndex = -1;
    private String[] choices = {};

    private static class Contact {
        String firstName;
        String lastName;
        String streetAddress;
        String city;
        String zipCode;
        String state;
        String country;
        String phone;

        @Override
        public String toString() {
            return "\n\tfirstName: " + firstName +
                    "\n\tlastName: " + lastName +
                    "\n\tstreetAddress: " + streetAddress +
                    "\n\tcity: " + city +
                    "\n\tzipCode: " + zipCode +
                    "\n\tstate: " + state +
                    "\n\tcountry: " + country +
                    "\n\tphone: " + phone;
        }
    }

    private final List<Runnable> operations = new ArrayList<>();

    public SwingHandler(SwingTextTerminal terminal) {
        this.terminal = terminal;

        this.backKeyStroke = terminal.getProperties().getString("custom.back.key", "ctrl U");

        terminal.registerHandler(KEY_STROKE_UP, t -> {
            if(choiceIndex < 0) {
                originalInput = terminal.getPartialInput();
            }
            if(choiceIndex < choices.length - 1) {
                choiceIndex++;
                t.replaceInput(choices[choiceIndex], false);
            }
            return new ReadHandlerData(ReadInterruptionStrategy.Action.CONTINUE);
        });

        terminal.registerHandler(KEY_STROKE_DOWN, t -> {
            if(choiceIndex >= 0) {
                choiceIndex--;
                String text = (choiceIndex < 0) ? originalInput : choices[choiceIndex];
                t.replaceInput(text, false);
            }
            return new ReadHandlerData(ReadInterruptionStrategy.Action.CONTINUE);
        });

        terminal.registerHandler(backKeyStroke, t -> new ReadHandlerData(ABORT));
    }

    public String getBackKeyStroke() {
        return backKeyStroke;
    }

    private void addTask(TextIO textIO, String prompt,
                         Supplier<String> defaultValueSupplier, Consumer<String> valueSetter, String... choices) {
        operations.add(() -> {
            setChoices(choices);
            valueSetter.accept(textIO.newStringInputReader()
                .withDefaultValue(defaultValueSupplier.get())
                .read(prompt));
        });
    }

    public void execute() {
        int step = 0;
        while(step < operations.size()) {
            terminal.setBookmark("bookmark_" + step);
            try {
                operations.get(step).run();
            } catch (ReadAbortedException e) {
                if(step > 0) step--;
                terminal.resetToBookmark("bookmark_" + step);
                continue;
            }
            step++;
        }
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + ": reading contact info.\n" +
                "(Illustrates how to use read handlers to allow going back to a previous field.)";
    }

    public void setChoices(String... choices) {
        this.originalInput = "";
        this.choiceIndex = -1;
        this.choices = choices;
    }

    public static void main(String[] args) {
        SwingTextTerminal terminal = new SwingTextTerminal();
        terminal.init();
        TextIO textIO = new TextIO(terminal);
        SwingHandler handler = new SwingHandler(terminal);

        terminal.println("----------------------------------------------------------------");
        terminal.println("|   Use the up and down arrow keys to scroll through choices.  |");
        terminal.println("|   Press '" + handler.getBackKeyStroke() + "' to go back to the previous field.           |");
        terminal.println("----------------------------------------------------------------\n");

        Contact contact = new Contact();
        handler.addTask(textIO, "First name", () -> contact.firstName, s -> contact.firstName = s,
                "albert", "alice", "ava", "betty", "cathy");
        handler.addTask(textIO, "Last name", () -> contact.lastName, s -> contact.lastName = s,
                "Adams", "Bush", "Clinton", "Eisenhower", "Ford");
        handler.addTask(textIO, "Street address", () -> contact.streetAddress, s -> contact.streetAddress = s);
        handler.addTask(textIO, "City", () -> contact.city, s -> contact.city = s,
                "Los Angeles", "New York", "San Francisco", "Washington");
        handler.addTask(textIO, "Zip code", () -> contact.zipCode, s -> contact.zipCode = s);
        handler.addTask(textIO, "State", () -> contact.state, s -> contact.state = s,
                "Alabama", "Alaska", "Arizona", "Arkansas", "California", "Colorado", "Connecticut", "Delaware");
        handler.addTask(textIO, "Country", () -> contact.country, s -> contact.country = s,
                "China", "France", "Germany", "Japan", "South Korea", "USA");
        handler.addTask(textIO, "Phone number", () -> contact.phone, s -> contact.phone = s);

        handler.execute();

        terminal.println("\nContact info: " + contact);

        textIO.newStringInputReader().withMinLength(0).read("\nPress enter to terminate...");
        textIO.dispose();
    }
}
