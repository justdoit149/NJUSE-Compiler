  .data
x1:
  .word 1
x2:
  .word 2
x3:
  .word 3

  .text
  .globl main
main:
  addi sp, sp, 0
mainEntry:

  li t0, 4
  mv a0, t0


  li t0, 5
  mv a1, t0

  la t0, x1
  lw a2, 0(t0)

  li t1, 2
  mul a3, a2, t1

  la t1, x1
  sw a3, 0(t1)

  la t0, x2
  lw a2, 0(t0)

  li t1, 2
  mul a3, a2, t1

  la t1, x2
  sw a3, 0(t1)

  la t0, x3
  lw a2, 0(t0)

  li t1, 2
  mul a3, a2, t1

  la t1, x3
  sw a3, 0(t1)

  mv a2, a0

  li t1, 2
  mul a3, a2, t1

  mv a0, a3

  mv a2, a1

  li t1, 2
  mul a3, a2, t1

  mv a1, a3

  la t0, x1
  lw a2, 0(t0)

  la t0, x2
  lw a3, 0(t0)

  add a4, a2, a3

  la t0, x3
  lw a2, 0(t0)

  add a3, a4, a2

  mv a2, a0

  add a0, a3, a2

  mv a2, a1

  add a1, a0, a2

  mv a0, a1
  addi sp, sp, 0
  li a7, 93
  ecall

