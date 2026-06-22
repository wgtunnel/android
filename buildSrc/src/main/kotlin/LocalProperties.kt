import java.io.File
import java.io.FileInputStream
import java.util.Properties

object LocalProperties {

    private val properties by lazy {
        val props = Properties()
        val file = File("local.properties")
        if (file.exists()) {
            FileInputStream(file).use { props.load(it) }
        }
        props
    }

    fun get(key: String): String? = properties.getProperty(key)

    fun getOrDefault(key: String, default: String): String = properties.getProperty(key, default)
}
