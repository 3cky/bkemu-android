# BkEmu-Android

![Bk0010-01-sideview](http://upload.wikimedia.org/wikipedia/commons/thumb/8/89/Bk0010-01-sideview.jpg/320px-Bk0010-01-sideview.jpg)

Данный репозиторий содержит исходные тексты приложения BkEmu - эмулятора семейства
PDP-11-совместимых советских 16-разрядных домашних компьютеров
[Электроника БК-0010/11М](http://ru.wikipedia.org/wiki/БК-0010) для платформы Android.

<a href="https://play.google.com/store/apps/details?id=su.comp.bk" alt="Download from Google Play">
  <img src="http://www.android.com/images/brand/android_app_on_play_large.png">
</a>

# Лицензия

* [GNU General Public License v.3.0](http://www.gnu.org/licenses/gpl-3.0.html)

# Эмулируемые функции

На данный момент поддерживается эмуляция:

 * **БК-0010-01**:
  * без внешних блоков (Бейсик Вильнюс)
  * с блоком МСТД (Фокал + тесты)
  * c блоком КНГМД с ДОЗУ 16КБ
 * **БК-0011М** с блоком МСТД или блоком КНГМД

Из аппаратной части эмулируются:

 * Процессор К1801ВМ1 (основной набор команд, за исключением специфичных для HALT-режима)
 * Видеоконтроллер К1801ВП1-037 (цветной и ч/б режимы, экранные палитры)
 * Контроллер клавиатуры К1801ВП1-014
 * Встроенный таймер К1801ВЕ1
 * Аудиовыход (PCM, бит 6 в регистре 0177716)
 * Системный таймер 11М (прерывание 50 Гц по вектору 100, бит 14 в регистре 0177662)
 * Страничная память 11М (биты 8-10, 12-14 в регистре 0177716)
 * Стандартный шестикнопочный джойстик на параллельном порту
 * Контроллер накопителя на гибких магнитных дисках К1801ВП1-128 (КНГМД,
 в режиме "только для чтения")

## Поддерживаемые форматы

Эмулятор поддерживает загрузку и сохранение образов программ в формате КУВТ-86 (.BIN) методом перехвата
прерывания `EMT 36` на БК-0010 или системного вызова `.BMB10` на БК-0011М, а также монтирование
образов гибких магнитных дисков в формате .IMG/.BKD (800 КБ).

## Сборка эмулятора

Для самостоятельной сборки приложения необходимо установить
[Android SDK](http://developer.android.com/sdk/index.html), а также
[Maven](http://maven.apache.org/download.html) версии 3.0.3 или выше. Помимо этого, необходимо
определить переменную окружения `ANDROID_HOME`, содержащую путь к Android SDK, например:

    export ANDROID_HOME=/opt/google/android-sdk-linux_x86

После этого сборка приложения осуществляется вызовом команды `mvn clean package` в директории,
содержащей исходные тексты приложения.

## Участие в разработке

Вы можете предлагать свои исправления и дополнения эмулятора, используя стандартные механизмы
GitHub fork и [pull requests](https://github.com/github/android/pulls).

## Контакты

Вопросы и пожелания, касающиеся работы эмулятора, направляйте по адресу:
<v.antonovich@gmail.com>.

---

# BkEmu-Android

This repository contains the source code for the BkEmu - emulator of 16-bit PDP-11-compatible
Soviet home computers [Elektronika BK-0010/11M](http://en.wikipedia.org/wiki/Elektronika\_BK) for
Android platform.

Please see the [issues](https://github.com/3cky/bkemu-android/issues) section to report any bugs or
feature requests and to see the list of known issues.

## License

* [GNU General Public License v.3.0](http://www.gnu.org/licenses/gpl-3.0.html)

## Building

The build requires [Maven](http://maven.apache.org/download.html) v3.0.3+ and the
[Android SDK](http://developer.android.com/sdk/index.html) to be installed in your
development environment. In addition you'll need to set the `ANDROID_HOME` environment variable
to the location of your SDK, i.e.:

    export ANDROID_HOME=/opt/google/android-sdk-linux_x86

After satisfying those requirements, the build is pretty simple - just run `mvn clean package` in
directory containing pulled source code.

## Contributing

Please fork this repository and contribute back using
[pull requests](https://github.com/github/android/pulls).

## Contacts

Feel free to send all your questions and suggestions about emulator to e-mail
<v.antonovich@gmail.com>.
