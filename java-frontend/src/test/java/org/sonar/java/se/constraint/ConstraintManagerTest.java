/*
 * SonarQube Java
 * Copyright (C) 2012-2020 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java.se.constraint;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.objectweb.asm.Opcodes;
import org.sonar.java.bytecode.cfg.Instruction;
import org.sonar.java.se.ProgramState;
import org.sonar.java.se.symbolicvalues.RelationalSymbolicValue;
import org.sonar.java.se.symbolicvalues.SymbolicValue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConstraintManagerTest {

  private static Stream<Arguments> data() {
    ImmutableList.Builder<Object[]> b = ImmutableList.builder();
    b.add(new Object[]{Opcodes.IF_ICMPEQ, RelationalSymbolicValue.Kind.EQUAL, false});
    b.add(new Object[]{Opcodes.IF_ACMPEQ, RelationalSymbolicValue.Kind.EQUAL, false});
    b.add(new Object[]{Opcodes.IFEQ, RelationalSymbolicValue.Kind.EQUAL, false});
    b.add(new Object[]{Opcodes.IFNULL, RelationalSymbolicValue.Kind.EQUAL, false});

    b.add(new Object[]{Opcodes.IFNE, RelationalSymbolicValue.Kind.NOT_EQUAL, false});
    b.add(new Object[]{Opcodes.IFNONNULL, RelationalSymbolicValue.Kind.NOT_EQUAL, false});
    b.add(new Object[]{Opcodes.IF_ICMPNE, RelationalSymbolicValue.Kind.NOT_EQUAL, false});
    b.add(new Object[]{Opcodes.IF_ACMPNE, RelationalSymbolicValue.Kind.NOT_EQUAL, false});

    b.add(new Object[]{Opcodes.IF_ICMPLT, RelationalSymbolicValue.Kind.LESS_THAN, false});
    b.add(new Object[]{Opcodes.IFLT, RelationalSymbolicValue.Kind.LESS_THAN, false});

    b.add(new Object[]{Opcodes.IF_ICMPGE, RelationalSymbolicValue.Kind.GREATER_THAN_OR_EQUAL, false});
    b.add(new Object[]{Opcodes.IFGE, RelationalSymbolicValue.Kind.GREATER_THAN_OR_EQUAL, false});

    b.add(new Object[]{Opcodes.IF_ICMPGT, RelationalSymbolicValue.Kind.LESS_THAN, true});
    b.add(new Object[]{Opcodes.IFGT, RelationalSymbolicValue.Kind.LESS_THAN, true});

    b.add(new Object[]{Opcodes.IF_ICMPLE, RelationalSymbolicValue.Kind.GREATER_THAN_OR_EQUAL, true});
    b.add(new Object[]{Opcodes.IFLE, RelationalSymbolicValue.Kind.GREATER_THAN_OR_EQUAL, true});
    return b.build()
      .stream()
      .map(data -> Arguments.of(data[0], data[1], data[2]));
  }

  @ParameterizedTest
  @MethodSource("data")
  void test_binary_sv_from_bytecode_inst(int opcode, RelationalSymbolicValue.Kind relKind, boolean reversed) throws Exception {
    SymbolicValue sv1 = new SymbolicValue();
    SymbolicValue sv2 = new SymbolicValue();
    SymbolicValue binarySV = createBinarySV(opcode, sv1, sv2);

    if (reversed) {
      assertThat(binarySV.computedFrom()).containsExactly(sv1, sv2);
    } else {
      assertThat(binarySV.computedFrom()).containsExactly(sv2, sv1);
    }
    assertThat(((RelationalSymbolicValue) binarySV).kind()).isSameAs(relKind);

  }

  private static SymbolicValue createBinarySV(int opcode, SymbolicValue sv1, SymbolicValue sv2) {
    ConstraintManager constraintManager = new ConstraintManager();
    List<ProgramState.SymbolicValueSymbol> computedFrom = Arrays.asList(new ProgramState.SymbolicValueSymbol(sv1, null), new ProgramState.SymbolicValueSymbol(sv2, null));
    Instruction inst = new Instruction(opcode);
    return constraintManager.createBinarySymbolicValue(inst, computedFrom);
  }

  @Test
  void illegalBinarySvCode() throws Exception {
    SymbolicValue sv1 = new SymbolicValue();
    SymbolicValue sv2 = new SymbolicValue();
    assertThatThrownBy(() -> createBinarySV(Opcodes.GOTO, sv1, sv2))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Unexpected kind for binary SV");
  }

}
