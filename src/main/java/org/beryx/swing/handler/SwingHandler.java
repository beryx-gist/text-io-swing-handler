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

import org.beryx.textio.*;
import org.beryx.textio.swing.SwingTextTerminal;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.beryx.textio.ReadInterruptionStrategy.Action.ABORT;

public class SwingHandler {
    private static final String KEY_STROKE_UP = "pressed UP";
    private static final String KEY_STROKE_DOWN = "pressed DOWN";

    private final TextIO textIO;
    private final SwingTextTerminal terminal;
    private final Object dataObject;

    private final String backKeyStroke;

    private String originalInput = "";
    private int choiceIndex = -1;
    private List<String> choices = new ArrayList<>();
    private List<String> filteredChoices = new ArrayList<>();

    private final List<Task> tasks = new ArrayList<>();

    public SwingHandler(TextIO textIO, Object dataObject) {
        this.textIO = textIO;
        this.terminal = (SwingTextTerminal)textIO.getTextTerminal();
        this.dataObject = dataObject;

        this.backKeyStroke = terminal.getProperties().getString("custom.back.key", "ctrl U");

        terminal.registerHandler(KEY_STROKE_UP, t -> {
            if(choiceIndex < 0) {
                originalInput = terminal.getPartialInput();
                filteredChoices = choices.stream().filter(choice -> choice.startsWith(originalInput)).collect(Collectors.toList());
            }
            if(choiceIndex < filteredChoices.size() - 1) {
                choiceIndex++;
                t.replaceInput(filteredChoices.get(choiceIndex), false);
            }
            return new ReadHandlerData(ReadInterruptionStrategy.Action.CONTINUE);
        });

        terminal.registerHandler(KEY_STROKE_DOWN, t -> {
            if(choiceIndex >= 0) {
                choiceIndex--;
                String text = (choiceIndex < 0) ? originalInput : filteredChoices.get(choiceIndex);
                t.replaceInput(text, false);
            }
            return new ReadHandlerData(ReadInterruptionStrategy.Action.CONTINUE);
        });

        terminal.registerHandler(backKeyStroke, t -> new ReadHandlerData(ABORT));
    }

    public TextIO getTextIO() {
        return textIO;
    }

    public String getBackKeyStroke() {
        return backKeyStroke;
    }

    public class Task<T,B extends Task<T,B>> implements Runnable {
        protected final String prompt;
        protected final Supplier<InputReader<T,?>> inputReaderSupplier;
        protected final Supplier<T> defaultValueSupplier;
        protected final Consumer<T> valueSetter;
        protected final List<T> choices = new ArrayList<>();

        public Task(String prompt, Supplier<InputReader<T, ?>> inputReaderSupplier, Supplier<T> defaultValueSupplier, Consumer<T> valueSetter) {
            this.prompt = prompt;
            this.inputReaderSupplier = inputReaderSupplier;
            this.defaultValueSupplier = defaultValueSupplier;
            this.valueSetter = valueSetter;
        }

        @Override
        public void run() {
            setChoices(choices.stream().map(Object::toString).collect(Collectors.toList()));
            valueSetter.accept(inputReaderSupplier.get()
                    .withDefaultValue(defaultValueSupplier.get())
                    .read(prompt));
        }

        public B addChoices(List<T> choices) {
            this.choices.addAll(choices);
            return (B)this;
        }
    }

    private void setChoices(List<String> choices) {
        this.originalInput = "";
        this.choiceIndex = -1;
        this.choices = choices;
    }

    public class StringTask extends Task<String, StringTask> {
        public StringTask(String fieldName, String prompt) {
            super(prompt,
                    () -> textIO.newStringInputReader(),
                    () -> getFieldValue(fieldName),
                    value -> setFieldValue(fieldName, value));
        }

        public StringTask addChoices(String... choices) {
            this.choices.addAll(Arrays.asList(choices));
            return this;
        }
    }

    public StringTask addStringTask(String fieldName, String prompt) {
        StringTask task = new StringTask(fieldName, prompt);
        tasks.add(task);
        return task;
    }


    public class IntTask extends Task<Integer, IntTask> {
        public IntTask(String fieldName, String prompt) {
            super(prompt,
                    () -> textIO.newIntInputReader(),
                    () -> getFieldValue(fieldName),
                    value -> setFieldValue(fieldName, value));
        }
        public IntTask addChoices(int... choices) {
            this.choices.addAll(IntStream.of(choices).boxed().collect(Collectors.toList()));
            return this;
        }
    }

    public IntTask addIntTask(String fieldName, String prompt) {
        IntTask task = new IntTask(fieldName, prompt);
        tasks.add(task);
        return task;
    }


    public class LongTask extends Task<Long, LongTask> {
        public LongTask(String fieldName, String prompt) {
            super(prompt,
                    () -> textIO.newLongInputReader(),
                    () -> getFieldValue(fieldName),
                    value -> setFieldValue(fieldName, value));
        }
        public LongTask addChoices(long... choices) {
            this.choices.addAll(LongStream.of(choices).boxed().collect(Collectors.toList()));
            return this;
        }
    }

    public LongTask addLongTask(String fieldName, String prompt) {
        LongTask task = new LongTask(fieldName, prompt);
        tasks.add(task);
        return task;
    }


    public class DoubleTask extends Task<Double, DoubleTask> {
        public DoubleTask(String fieldName, String prompt) {
            super(prompt,
                    () -> textIO.newDoubleInputReader(),
                    () -> getFieldValue(fieldName),
                    value -> setFieldValue(fieldName, value));
        }
        public DoubleTask addChoices(double... choices) {
            this.choices.addAll(DoubleStream.of(choices).boxed().collect(Collectors.toList()));
            return this;
        }
    }

    public DoubleTask addDoubleTask(String fieldName, String prompt) {
        DoubleTask task = new DoubleTask(fieldName, prompt);
        tasks.add(task);
        return task;
    }


// TODO - implement Task specializations for: boolean, byte, char, enum, float, short etc.


    public void execute() {
        int step = 0;
        while(step < tasks.size()) {
            terminal.setBookmark("bookmark_" + step);
            try {
                tasks.get(step).run();
            } catch (ReadAbortedException e) {
                if(step > 0) step--;
                terminal.resetToBookmark("bookmark_" + step);
                continue;
            }
            step++;
        }
    }

    private Field getField(String fieldName) {
        try {
            return dataObject.getClass().getField(fieldName);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("Unknown field: " + fieldName);
        }
    }

    private <V> V getFieldValue(String fieldName) {
        try {
            return (V) getField(fieldName).get(dataObject);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot retrieve value of " + fieldName, e);
        }
    }

    private <V> void setFieldValue(String fieldName, V value) {
        try {
            getField(fieldName).set(dataObject, value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot set value of " + fieldName, e);
        }
    }
}
