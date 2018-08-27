# BkEmu-Android

![Bk0010-01-sideview](https://upload.wikimedia.org/wikipedia/commons/thumb/8/89/Bk0010-01-sideview.jpg/320px-Bk0010-01-sideview.jpg)

Данный репозиторий содержит исходные тексты приложения BkEmu - эмулятора семейства
PDP-11-совместимых советских 16-разрядных домашних компьютеров
[Электроника БК-0010/11М](https://ru.wikipedia.org/wiki/БК-0010) для платформы Android.

<a href="https://play.google.com/store/apps/details?id=su.comp.bk" alt="Download from Google Play">
  <img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" height="80">
</a>

# Лицензия

* [GNU General Public License v.3.0](https://www.gnu.org/licenses/gpl-3.0.html)

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

Проект использует систему сборки [Gradle](https://gradle.org/).

Исходные тексты проекта можно получить командой:

```
git clone https://github.com/3cky/bkemu-android.git
```

Также исходные тексты доступны в виде [архива](https://github.com/3cky/bkemu-android/archive/master.zip).

После этого импортируйте проект в [Android Studio](https://developer.android.com/studio/) (опция "Import Project"),
указав директорию с загруженными исходными текстами.

Также можно собрать проект в консоли командой `./gradlew build`.

## Участие в разработке

Вы можете предлагать свои исправления и дополнения эмулятора, используя стандартные механизмы
GitHub fork и [pull requests](https://github.com/github/android/pulls).

## Контакты

Вопросы и пожелания, касающиеся работы эмулятора, направляйте по адресу:
<v.antonovich@gmail.com>.

---

# BkEmu-Android

This repository contains the source code for the BkEmu - emulator of 16-bit PDP-11-compatible
Soviet home computers [Elektronika BK-0010/11M](https://en.wikipedia.org/wiki/Elektronika\_BK) for
Android platform.

Please see the [issues](https://github.com/3cky/bkemu-android/issues) section to report any bugs or
feature requests and to see the list of known issues.

## License

* [GNU General Public License v.3.0](https://www.gnu.org/licenses/gpl-3.0.html)

## Building

This project uses the [Gradle](https://gradle.org/) build system.

First download the sources by cloning this repository or downloading an
[archived snapshot](https://github.com/3cky/bkemu-android/archive/master.zip).

In [Android Studio](https://developer.android.com/studio/) use the "Import Project" option.
Next select the directory that you downloaded from this repository.

Alternatively use the `./gradlew build` command to build the project directly.

## Contributing

Please fork this repository and contribute back using
[pull requests](https://github.com/github/android/pulls).

## Contacts

Feel free to send all your questions and suggestions about emulator to e-mail
<v.antonovich@gmail.com>.
