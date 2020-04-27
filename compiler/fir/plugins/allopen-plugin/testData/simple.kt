// FILE: annotations.kt

package allopen

annotation class Open

// FILE: main.kt

import allopen.Open

@Open
class A {
    @Open
    fun foo() {

    }
}

class B : A() {
    override fun foo() {

    }
}