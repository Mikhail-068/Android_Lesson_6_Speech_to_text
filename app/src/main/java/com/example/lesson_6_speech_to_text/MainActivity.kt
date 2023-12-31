package com.example.lesson_6_speech_to_text

import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.entityextraction.DateTimeEntity
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractionRemoteModel
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val CODE = 10 // код для запуска голосового ввода
private const val TAG = "MyTag"  // константа для тега (поможет найти наши сообщения в логе)

class MainActivity : AppCompatActivity() {
    private lateinit var btnSpeech: Button      // переменная для кнопки, которая запускает распознавание речи
    private lateinit var btnCommand: Button     // переменная для кнопки, которая запускает распознавание команд
    private lateinit var textField: TextView    // переменная для текстового поля

    // переменная-флаг для работы с распознанным текстом:
    // если флаг = true, то мы ожидаем, что пользователь сказал команду
    private var cmdFlag = false

    // создаем объект для работы с моделью, которая может находить
    private val entityExtractor = EntityExtraction
        .getClient(EntityExtractorOptions.Builder(EntityExtractorOptions.RUSSIAN).build())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnSpeech = findViewById(R.id.btn_speech)   // находим кнопку
        btnCommand = findViewById(R.id.btn_cmd)     // находим кнопку
        textField = findViewById(R.id.text)         // находим текстовое поле

        //----------------------------------------------------------
        // загружаем модель для нахождения сущностей в тексте, если требуется
        entityExtractor.downloadModelIfNeeded().apply {
            // если все окей, то выводим надпись в лог
            addOnSuccessListener {
                Log.d(TAG, "Model was downloaded successful!")
            }

            // если ошибка, то грузим модель из интернета
            addOnFailureListener {
                Log.d(TAG, "Start downloading of the model!")
                val modelManager = RemoteModelManager.getInstance()
                val ruLangModel =
                    EntityExtractionRemoteModel.Builder(EntityExtractorOptions.RUSSIAN).build()
                val conditions = DownloadConditions.Builder().requireWifi().build()

                // загружаем модель в память устройства, чтобы не качать каждый раз
                modelManager.download(ruLangModel, conditions)
                    .addOnSuccessListener {
                        Log.d(TAG, "Model was downloaded successful!")
                    }
                    .addOnFailureListener {
                        Log.d(TAG, "Error while model downloading: ${it.message}")
                    }
            }
        }


        //----------------------------------------------------------
        // подготовим запуск распознавания речи
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            // добавим текстовую модель
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            // добавим язык, который будет распознаваться (в нашем случае будет тот, что в устройстве)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        //----------------------------------------------------------
        // установим обработчик нажатий на кнопку распознавания речи
        btnSpeech.setOnClickListener {
            // меняем значение флага
            cmdFlag = false
            // запускаем активити с распознаванием речи
            startActivityForResult(intent, CODE)
        }

        //----------------------------------------------------------
        // установим обработчик нажатий на кнопку распознавания команд
        btnCommand.setOnClickListener {
            // меняем значение флага
            cmdFlag = true
            // запускаем активити с распознаванием речи
            startActivityForResult(intent, CODE)
        }
    }


    // Этот метод вызывается после окончания работы с микрофоном
    // (когда пользователь закончил произносить фразу)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // проверяем, что результат соответствует нашему коду запроса и он не пустой
        if (requestCode == CODE && resultCode == RESULT_OK && data != null) {

            // получаем список результатов (их всегда несколько)
            val recognitionResultList =
                data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) as ArrayList<String>

            //  будем работать с первым результатом
            //  проверяем, что в нем есть какие-то данные
            if (recognitionResultList.first().isNotEmpty() && recognitionResultList.first().isNotBlank()) {

                //-----------------------------------------------------------------
                //--------  ЕСЛИ РАБОТАЕМ В РЕЖИМЕ РАСПОЗНАВАНИЯ КОМАНД    --------
                if (cmdFlag) {
                    commandProcessing(recognitionResultList.first())
                }

                //---------------------------------------------------------------
                //--------  ЕСЛИ РАБОТАЕМ В РЕЖИМЕ РАСПОЗНАВАНИЯ РЕЧИ    --------
                else {
                    // устанавливаем результат в текстовое поле
                    textField.text = recognitionResultList.first()

                    // вызываем функцию, которая находит сущности в тексте (будет искать дату/время)
                    findDateTimeInText(recognitionResultList.first())
                }

                // пишем в лог
                Log.i(TAG, "Recognition result: " + recognitionResultList.first())
            } else {
                // если в первом элементе нет данных, то выводим ошибку в текстовое поле
                textField.text = "ОШИБКА! \n Нажмите на кнопку и повторите фразу"

                // пишем ошибку в лог
                Log.e(TAG, "Error! Recognition result is blank or empty!")
            }
        } else {
            // выводим ошибку в текстовое поле, если что-то пошло не так
            textField.text = "ОШИБКА! \n Нажмите на кнопку и повторите фразу"

            // пишем ошибку в лог
            Log.e(TAG, "Recognition error!")
        }
    }

    // функция для нахождения сущности в тексте (мы будем искать время)
    private fun findDateTimeInText(text: String) {
        // уберем лишние пробелы (если они есть) и переведем текст в нижний регистр
        val modifyText = text.trim().lowercase()

        // закинем наш текст в специальный объект, который будет анализироваться моделью
        val params = EntityExtractionParams.Builder(modifyText).build()

        // запускаем поиск сущностей в тексте
        entityExtractor.annotate(params).apply {
            // если нашли сущности в тексте
            addOnSuccessListener { entityAnnotations ->

                // перебираем список найденных участков текста, где есть сущности
                for (entityAnnotation in entityAnnotations) {

                    // выделяем сущности во фрагменте в отельный лист
                    // (их может быть несколько внутри одного фрагмента)
                    val entities: List<Entity> = entityAnnotation.entities

                    // бежим всем найденным сущностям
                    for (entity in entities) {
                        when (entity) {
                            // если сущность относится к категории времени
                            is DateTimeEntity -> {
                                Log.d(TAG, "найдена сущность типа `дата/время`")

                                // переводим полученное время в удобный для отображения формат
                                val foundedTime = convertMsToFormattedLocalDateTime(entity.timestampMillis)

                                // берем старую строку, что была на экране, чтобы ее дополнить
                                // сообщением о найденной дате/времени
                                val oldText = textField.text.toString().substringBefore("\n")

                                // дополняем старый текст и выводим на экран
                                textField.text = oldText + "\n\n" +
                                        "В вашем сообщении было найдено упоминание времени/даты:\n" +
                                        "$foundedTime"
                            }

                            // в любом другом случае
                            else -> {
                                Log.d(TAG, "сущности типа `дата/время` не найдены")
                            }
                        }

                    }
                }

                // если не нашли сущности в тексте
                addOnFailureListener {
                    Log.e(TAG, "в тексте не найдено сущностей!!!")
                }
            }
        }
    }

    private fun convertMsToFormattedLocalDateTime(millis: Long) : String{
        val dateTimeValue = Instant.ofEpochMilli(millis)
        val localDateTime = dateTimeValue.atZone(
            ZoneOffset.systemDefault()
        ).toLocalDateTime()

        val formatter = DateTimeFormatter.ofPattern("HH:mm dd.MM.yyyy")
        return localDateTime.format(formatter)
    }


    // функция для обработки команд
    private fun commandProcessing(commandText: String) {
        // уберем лишние пробелы (если они есть) и переведем текст в нижний регистр
        val modifyText = commandText.trim().lowercase()

        // аналог switch-case, в качестве параметра - текст команды
        when (modifyText) {

            // ---------------------    ВКЛ ФОНАРИК    ---------------------
            "включить фонарик" -> {
                // на всякий случай подобные операции лучше заворачивать в try-catch блок,
                // т.к. они аппаратно зависимы
                try {
                    // получаем менеджер камер
                    val cameraManager = this@MainActivity.getSystemService(CAMERA_SERVICE) as CameraManager

                    // берем ID основной камеры (по-умолчанию она первая)
                    val camera = cameraManager.cameraIdList[0]

                    // включаем вспышку
                    cameraManager.setTorchMode(camera, true)

                    // выводим тост и пишем в лог
                    Toast.makeText(this@MainActivity, "Фонарик включен!", Toast.LENGTH_LONG).show()
                    Log.i(TAG, "Flashlight is turned on")
                } catch (e: CameraAccessException){
                    // выводим тост с ошибкой и пишем в лог
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "can't turn the flashlight on! Error: ${e.message}")
                }
            }

            // ---------------------    ВЫКЛ ФОНАРИК    ---------------------
            "выключить фонарик" -> {
                try {
                    val cameraManager = this@MainActivity.getSystemService(CAMERA_SERVICE) as CameraManager
                    val camera = cameraManager.cameraIdList[0]
                    cameraManager.setTorchMode(camera, false)
                    Toast.makeText(this@MainActivity, "Фонарик выключен!", Toast.LENGTH_LONG).show()
                    Log.i(TAG, "Flashlight is turned off")
                } catch (e: CameraAccessException){
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "can't turn the flashlight off! Error: ${e.message}")
                }
            }


            // ---------------------    ОТКРЫТЬ КАРТЫ    ---------------------
            "открыть карту" -> {
                // задаем запрос, который закинем в карты
                val location = Uri.parse("geo:0,0?q=Moscow")
                // можно еще просто задать координаты (параметр z - это просто масштаб), например:
                // Uri location = Uri.parse("geo:37.422219,-122.08364?z=14");

                // формируем намерение открыть карты
                val mapIntent = Intent(Intent.ACTION_VIEW, location)

                // выводим тост и пишем в лог
                Toast.makeText(this@MainActivity, "Открываю карту!", Toast.LENGTH_LONG).show()
                Log.i(TAG, "Maps are opened")

                // запускаем активити с картой
                startActivity(mapIntent)
            }


            // ---------------------    ОТКРЫТЬ ГУГЛ    ---------------------
            "открыть браузер" -> {
                // задаем адрес
                val webpage = Uri.parse("http://www.google.com")

                // формируем намерение открыть браузер
                val intent = Intent(Intent.ACTION_VIEW, webpage)

                // выводим тост и пишем в лог
                Toast.makeText(this@MainActivity, "Открываю браузер!", Toast.LENGTH_LONG).show()
                Log.i(TAG, "Browser is opened")

                // запускаем активити с браузером
                startActivity(intent)
            }

            // ---------------------    ОБРАБОТКА ЛЮОГО ДРУГОГО РЕЗУЛЬТАТА    ---------------------
            // в любом другом случае выводим ошибку
            else -> {
                Toast.makeText(
                    this@MainActivity,
                    "Ошибка, команда не распознана! Попробуйте еще раз!",
                    Toast.LENGTH_LONG
                ).show()
                Log.e(TAG, "Can't recognize the command!")
            }
        }
    }

}