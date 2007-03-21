/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiManager;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.AbstractConvertContext;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.NamedEnumUtil;
import com.intellij.util.xml.ResolvingConverter;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.highlighting.DomElementProblemDescriptor;
import com.intellij.util.xml.highlighting.DomElementsProblemsHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
public class ComboControl extends BaseModifiableControl<JComboBox, String> {
  private static final Pair<String, Icon> EMPTY = new ComboBoxItem(" ", null);
  private final Factory<List<Pair<String, Icon>>> myDataFactory;
  private boolean myNullable;
  private final Map<String, Icon> myIcons = new HashMap<String, Icon>();
  private final ItemListener myCommitListener = new ItemListener() {
    public void itemStateChanged(ItemEvent e) {
      setModified();
      commit();
    }
  };

  public ComboControl(final GenericDomValue genericDomValue, final Factory<List<Pair<String, Icon>>> dataFactory) {
    this(new DomStringWrapper(genericDomValue), dataFactory);
  }

  public ComboControl(final DomWrapper<String> domWrapper, final Factory<List<Pair<String, Icon>>> dataFactory) {
    super(domWrapper);
    myDataFactory = dataFactory;
    reset();
  }

  public ComboControl(final DomWrapper<String> domWrapper, final Class<? extends Enum> aClass) {
    super(domWrapper);
    myDataFactory = createEnumFactory(aClass);
    reset();
  }

  public final boolean isNullable() {
    return myNullable;
  }

  public final void setNullable(final boolean nullable) {
    myNullable = nullable;
  }

  public ComboControl(final GenericDomValue<?> reference) {
    this(reference, createResolvingFunction(reference));
  }

  public static Factory<List<Pair<String, Icon>>> createResolvingFunction(final GenericDomValue<?> reference) {
    return new Factory<List<Pair<String, Icon>>>() {
      public List<Pair<String, Icon>> create() {
        final Converter converter = reference.getConverter();
        if (converter instanceof ResolvingConverter) {
          final AbstractConvertContext context = new AbstractConvertContext() {
            @NotNull
            public DomElement getInvocationElement() {
              return reference;
            }

            public PsiManager getPsiManager() {
              return getFile().getManager();
            }
          };
          final ResolvingConverter resolvingConverter = (ResolvingConverter)converter;
          final Collection<Object> variants = resolvingConverter.getVariants(context);
          final List<Pair<String, Icon>> all = ContainerUtil.map(variants, new Function<Object, Pair<String, Icon>>() {
            public Pair<String, Icon> fun(final Object s) {
              return Pair.create(ElementPresentationManager.getElementName(s), ElementPresentationManager.getIcon(s));
            }
          });
          all.addAll(ContainerUtil.map(resolvingConverter.getAdditionalVariants(), new Function() {
            public Object fun(final Object s) {
              return new Pair(s, null);
            }
          }));
          return all;
        }
        return Collections.emptyList();
      }
    };
  }

  public static Factory<Collection<? extends Object>> createVariantsGetter(final GenericDomValue<?> reference) {
    return new Factory<Collection<? extends Object>>() {
      public Collection<? extends Object> create() {
        final Converter converter = reference.getConverter();
        if (converter instanceof ResolvingConverter) {
          return ((ResolvingConverter)converter).getVariants(new AbstractConvertContext() {
            @NotNull
            public DomElement getInvocationElement() {
              return reference;
            }

            public PsiManager getPsiManager() {
              return getFile().getManager();
            }
          });

        }
        return Collections.emptyList();
      }
    };
  }

  public static Factory<List<Pair<String, Icon>>> createPresentationFunction(final Factory<Collection<? extends Object>> variantFactory) {
    return new Factory<List<Pair<String, Icon>>>() {
      public List<Pair<String, Icon>> create() {

        return ContainerUtil.map(variantFactory.create(), new Function<Object, Pair<String, Icon>>() {
          public Pair<String, Icon> fun(final Object s) {
            return Pair.create(ElementPresentationManager.getElementName(s), ElementPresentationManager.getIcon(s));
          }
        });

      }
    };
  }

  static Factory<List<Pair<String, Icon>>> createEnumFactory(final Class<? extends Enum> aClass) {
    return new Factory<List<Pair<String, Icon>>>() {
      public List<Pair<String, Icon>> create() {
        return ContainerUtil.map2List(aClass.getEnumConstants(), new Function<Enum, Pair<String, Icon>>() {
          public Pair<String, Icon> fun(final Enum s) {
            return Pair.create(NamedEnumUtil.getEnumValueByElement(s), ElementPresentationManager.getIcon(s));
          }
        });
      }
    };
  }

  public static <T extends Enum> JComboBox createEnumComboBox(final Class<T> type) {
    return tuneUpComboBox(new JComboBox(), createEnumFactory(type));
  }

  private static JComboBox tuneUpComboBox(final JComboBox comboBox, Factory<List<Pair<String, Icon>>> dataFactory) {
    final List<Pair<String, Icon>> list = dataFactory.create();
    final Set<String> standardValues = new HashSet<String>();
    for (final Pair<String, Icon> pair : list) {
      comboBox.addItem(new ComboBoxItem(pair));
      standardValues.add(pair.first);
    }
    return initComboBox(comboBox, new Condition<String>() {
      public boolean value(final String object) {
        return standardValues.contains(object);
      }
    });
  }

  private static class ComboBoxItem extends Pair<String,Icon> {

    public ComboBoxItem(String first, Icon second) {
      super(first, second);
    }

    public ComboBoxItem(Pair<String,Icon> pair) {
      super(pair.first, pair.second);
    }

    public String toString() {
      return first;
    }
  }

  static JComboBox initComboBox(final JComboBox comboBox, final Condition<String> validity) {
    comboBox.setEditable(false);
    comboBox.setPrototypeDisplayValue(new ComboBoxItem("A", null));
    comboBox.setRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        final Pair<String, Icon> pair = (Pair<String, Icon>)value;
        final String text = pair == null ? null : pair.first;
        setText(text);
        final Dimension dimension = getPreferredSize();
        if (!validity.value(text)) {
          setFont(getFont().deriveFont(Font.ITALIC));
          setForeground(Color.RED);
        }
        setIcon(pair == null ? null : pair.second);
        setPreferredSize(new Dimension(-1, dimension.height));
        return this;
      }
    });
    return comboBox;
  }

  protected JComboBox createMainComponent(final JComboBox boundedComponent) {
    return initComboBox(boundedComponent == null ? new JComboBox() : boundedComponent, new Condition<String>() {
      public boolean value(final String object) {
        return isValidValue(object);
      }
    });
  }

  public boolean isValidValue(final String object) {
    return myNullable && object == EMPTY.first || myIcons.containsKey(object);
  }

  private boolean dataChanged(List<Pair<String, Icon>> newData) {
    final JComboBox comboBox = getComponent();
    final int size = comboBox.getItemCount();
    final List<Pair<String, Icon>> oldData = new ArrayList<Pair<String, Icon>>(size);
    for (int i = 0; i < size; i++) {
      oldData.add((Pair<String, Icon>)comboBox.getItemAt(i));
    }

    if (myNullable) {
      final LinkedList<Pair<String, Icon>> list = new LinkedList<Pair<String, Icon>>(newData);
      list.addFirst(EMPTY);
      newData = list;
    }

    return !newData.equals(oldData);
  }

  protected boolean isCommitted() {
    return getComponent().isPopupVisible() || super.isCommitted();
  }

  protected void doReset() {
    final List<Pair<String, Icon>> data = myDataFactory.create();
    final JComboBox comboBox = getComponent();
    comboBox.removeItemListener(myCommitListener);
    try {
      if (!dataChanged(data)) {
        super.doReset();
        return;
      }

      final String oldValue = getValue();
      myIcons.clear();
      comboBox.removeAllItems();
      if (myNullable) {
        comboBox.addItem(EMPTY);
      }
      for (final Pair<String, Icon> s : data) {
        comboBox.addItem(new ComboBoxItem(s));
        myIcons.put(s.first, s.second);
      }
      setValue(oldValue);
      super.doReset();
    }
    finally {
      comboBox.addItemListener(myCommitListener);
    }
  }

  @Nullable
  protected final String getValue() {
    final Pair<String, Icon> pair = (Pair<String, Icon>)getComponent().getSelectedItem();
    return pair == null || pair == EMPTY ? null : pair.first;
  }

  protected final void setValue(final String value) {
    final JComboBox component = getComponent();
    if (!isValidValue(value)) {
      component.setEditable(true);
    }
    component.setSelectedItem(new ComboBoxItem(value, myIcons.get(value)));
    component.setEditable(false);
  }


  protected void updateComponent() {
    final DomElement domElement = getDomElement();
    if (domElement == null || !domElement.isValid()) return;

    final JComboBox comboBox = getComponent();

    final Project project = getProject();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (!project.isOpen()) return;
        if (!getDomWrapper().isValid()) return;

        final DomElement domElement = getDomElement();
        if (domElement == null || !domElement.isValid()) return;

        final DomElementAnnotationsManager manager = DomElementAnnotationsManager.getInstance(project);
        final DomElementsProblemsHolder holder = manager.getCachedProblemHolder(domElement);
        final List<DomElementProblemDescriptor> errorProblems = holder.getProblems(domElement);
        final List<DomElementProblemDescriptor> warningProblems = holder.getProblems(domElement, true, HighlightSeverity.WARNING);

        Color background = getDefaultBackground();
        comboBox.setToolTipText(null);

        if (errorProblems.size() > 0) {
          background = getErrorBackground();
          comboBox.setToolTipText(TooltipUtils.getTooltipText(errorProblems));
        }
        else if (warningProblems.size() > 0) {
          background = getWarningBackground();
          comboBox.setToolTipText(TooltipUtils.getTooltipText(warningProblems));
        }

            final Pair<String, Icon> pair = (Pair<String, Icon>)comboBox.getSelectedItem();
            final String s = pair == null ? null : pair.first;
            background = s != null && s.trim().length() > 0 ? getDefaultBackground() : background;

        comboBox.setBackground(background);
        comboBox.getEditor().getEditorComponent().setBackground(background);
      }
    });

  }
}
