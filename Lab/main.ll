; ModuleID = 'module'
source_filename = "module"

define i32 @main() {
mainEntry:
  %x1 = alloca i32, align 4
  store i32 1, i32* %x1, align 4
  %x2 = alloca i32, align 4
  store i32 2, i32* %x2, align 4
  %x3 = alloca i32, align 4
  store i32 3, i32* %x3, align 4
  %x4 = alloca i32, align 4
  store i32 4, i32* %x4, align 4
  %x5 = alloca i32, align 4
  store i32 5, i32* %x5, align 4
  %x11 = load i32, i32* %x1, align 4
  %tmp = mul i32 %x11, 2
  store i32 %tmp, i32* %x1, align 4
  %x22 = load i32, i32* %x2, align 4
  %tmp3 = mul i32 %x22, 2
  store i32 %tmp3, i32* %x2, align 4
  %x34 = load i32, i32* %x3, align 4
  %tmp5 = mul i32 %x34, 2
  store i32 %tmp5, i32* %x3, align 4
  %x46 = load i32, i32* %x4, align 4
  %tmp7 = mul i32 %x46, 2
  store i32 %tmp7, i32* %x4, align 4
  %x58 = load i32, i32* %x5, align 4
  %tmp9 = mul i32 %x58, 2
  store i32 %tmp9, i32* %x5, align 4
  %x110 = load i32, i32* %x1, align 4
  %x211 = load i32, i32* %x2, align 4
  %tmp12 = add i32 %x110, %x211
  %x313 = load i32, i32* %x3, align 4
  %tmp14 = add i32 %tmp12, %x313
  %x415 = load i32, i32* %x4, align 4
  %tmp16 = add i32 %tmp14, %x415
  %x517 = load i32, i32* %x5, align 4
  %tmp18 = add i32 %tmp16, %x517
  ret i32 %tmp18
}
