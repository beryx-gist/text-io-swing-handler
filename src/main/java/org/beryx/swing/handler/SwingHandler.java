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

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

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

    private final Supplier<StringInputReader> stringInputReaderSupplier;
    private final Supplier<IntInputReader> intInputReaderSupplier;
    private final Supplier<LongInputReader> longInputReaderSupplier;
    private final Supplier<DoubleInputReader> doubleInputReaderSupplier;
    
    private final List<Task<?,?,?>> tasks = new ArrayList<>();

    public SwingHandler(TextIO textIO, Object dataObject) {
        this.textIO = textIO;
        this.terminal = (SwingTextTerminal)textIO.getTextTerminal();
        this.dataObject = dataObject;
        
        this.stringInputReaderSupplier = () -> textIO.newStringInputReader();
        this.intInputReaderSupplier = () -> textIO.newIntInputReader();
        this.longInputReaderSupplier = () -> textIO.newLongInputReader();
        this.doubleInputReaderSupplier = () -> textIO.newDoubleInputReader();

        this.backKeyStroke = terminal.getProperties().getString("custom.back.key", "ctrl U");

        terminal.getDocument().addDocumentListener(new DocumentListener() {			
			@Override public void removeUpdate(DocumentEvent e) {choiceIndex = -1;}
			@Override public void insertUpdate(DocumentEvent e) {choiceIndex = -1;}
			@Override public void changedUpdate(DocumentEvent e) {choiceIndex = -1;}
		});
        
        terminal.registerHandler(KEY_STROKE_UP, t -> {
            if(choiceIndex < 0) {
                originalInput = terminal.getPartialInput();
                filteredChoices = choices.stream()
                		.filter(choice -> choice.toLowerCase().startsWith(originalInput.toLowerCase()))
                		.collect(Collectors.toList());
            }
            if(choiceIndex < filteredChoices.size() - 1) {
                int savedChoiceIndex = ++choiceIndex;
                t.replaceInput(filteredChoices.get(choiceIndex), false);
                choiceIndex = savedChoiceIndex;
            }
            return new ReadHandlerData(ReadInterruptionStrategy.Action.CONTINUE);
        });

        terminal.registerHandler(KEY_STROKE_DOWN, t -> {
            if(choiceIndex >= 0) {
                int savedChoiceIndex = --choiceIndex;
                String text = (choiceIndex < 0) ? originalInput : filteredChoices.get(choiceIndex);
                t.replaceInput(text, false);
                choiceIndex = savedChoiceIndex;
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

    public class Task<T,B extends Task<T,B, R>, R extends InputReader<T, ?>> implements Runnable {
        protected final String prompt;
        protected final Supplier<R> inputReaderSupplier;
        protected final Supplier<T> defaultValueSupplier;
        protected final Consumer<T> valueSetter;
        protected final List<T> choices = new ArrayList<>();
        protected boolean constrainedInput;
        protected Consumer<R> inputReaderConfigurator;

        public Task(String prompt, Supplier<R> inputReaderSupplier, Supplier<T> defaultValueSupplier, Consumer<T> valueSetter) {
            this.prompt = prompt;
            this.inputReaderSupplier = inputReaderSupplier;
            this.defaultValueSupplier = defaultValueSupplier;
            this.valueSetter = valueSetter;
        }

        @Override
        public void run() {
            setChoices(choices.stream().map(Object::toString).collect(Collectors.toList()));
            R inputReader = inputReaderSupplier.get();
            inputReader.withDefaultValue(defaultValueSupplier.get());
            if(inputReaderConfigurator != null) {
            	inputReaderConfigurator.accept(inputReader);
            }
            if(constrainedInput) {
            	inputReader.withValueChecker((val,name) -> choices.contains(val) ? null 
            			: Arrays.asList("'" + val + "' is not in the choice list."));

            }
            valueSetter.accept(inputReader.read(prompt));
        }

        @SuppressWarnings("unchecked")
		public B withInputReaderConfigurator(Consumer<R> configurator) {
            this.inputReaderConfigurator = configurator;
            return (B)this;
        }
        
        @SuppressWarnings("unchecked")
		public B addChoices(List<T> choices) {
            this.choices.addAll(choices);
            return (B)this;
        }
        
        public void constrainInputToChoices() {
        	this.constrainedInput = true;
        }
    }

    private void setChoices(List<String> choices) {
        this.originalInput = "";
        this.choiceIndex = -1;
        this.choices = choices;
    }

    private final <T> Supplier<T> getDefaultValueSupplier(String fieldName) {
    	return () -> getFieldValue(fieldName);
    }
    
    private final <T> Consumer<T> getValueSetter(String fieldName) {
    	return value -> setFieldValue(fieldName, value);
    }
    
    public class StringTask extends Task<String, StringTask, StringInputReader> {
        public StringTask(String fieldName, String prompt) {
            super(prompt,
                    stringInputReaderSupplier,
                    getDefaultValueSupplier(fieldName),
                    getValueSetter(fieldName));
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


    public class IntTask extends Task<Integer, IntTask, IntInputReader> {
        public IntTask(String fieldName, String prompt) {
            super(prompt,
                    intInputReaderSupplier,
                    getDefaultValueSupplier(fieldName),
                    getValueSetter(fieldName));
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


    public class LongTask extends Task<Long, LongTask, LongInputReader> {
        public LongTask(String fieldName, String prompt) {
            super(prompt,
                    longInputReaderSupplier,
                    getDefaultValueSupplier(fieldName),
                    getValueSetter(fieldName));
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


    public class DoubleTask extends Task<Double, DoubleTask, DoubleInputReader> {
        public DoubleTask(String fieldName, String prompt) {
            super(prompt,
                    doubleInputReaderSupplier,
                    getDefaultValueSupplier(fieldName),
                    getValueSetter(fieldName));
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

    @SuppressWarnings("unchecked")
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
