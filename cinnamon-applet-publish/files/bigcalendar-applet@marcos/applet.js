const Applet = imports.ui.applet;
const GLib = imports.gi.GLib;
const PopupMenu = imports.ui.popupMenu;
const St = imports.gi.St;
const Clutter = imports.gi.Clutter;
const ByteArray = imports.byteArray;
const Mainloop = imports.mainloop;

class BigCalendarApplet extends Applet.TextApplet {
    constructor(metadata, orientation, panel_height, instance_id) {
        super(orientation, panel_height, instance_id);
        
        this.set_applet_tooltip("Gerenciador do Big Calendar");

        // State variables
        this.currentMonthDate = new Date(); // Mês visualizado atualmente
        this.selectedDate = new Date(); // Dia selecionado atualmente
        this.activities = [];

        // Iniciar o relógio no painel
        this._updateClock();
        this._clockTimerId = Mainloop.timeout_add_seconds(1, () => this._updateClock());

        // Configurar Menu Popup
        this.menuManager = new PopupMenu.PopupMenuManager(this);
        this.menu = new Applet.AppletPopupMenu(this, orientation);
        this.menuManager.addMenu(this.menu);

        // Layout Principal: Container Horizontal
        this.mainLayout = new St.BoxLayout({ vertical: false, style: "spacing: 16px; padding: 12px;" });

        // Criar uma seção de menu e adicionar o layout principal nela
        this.menuSection = new PopupMenu.PopupMenuSection();
        this.menuSection.actor.add_actor(this.mainLayout);
        this.menu.addMenuItem(this.menuSection);

        // Painel Esquerdo: Lista de Compromissos
        this.leftPane = new St.BoxLayout({ vertical: true, style: "spacing: 8px; width: 240px;" });
        this.mainLayout.add_actor(this.leftPane);

        // Linha Divisória Vertical
        let divider = new St.BoxLayout({
            style: "width: 1px; background-color: rgba(128, 128, 128, 0.2); margin-top: 4px; margin-bottom: 4px;"
        });
        this.mainLayout.add_actor(divider);

        // Painel Direito: Calendário Mensal
        this.rightPane = new St.BoxLayout({ vertical: true, style: "spacing: 8px; width: 250px;" });
        this.mainLayout.add_actor(this.rightPane);

        // Recarregar dados sempre que o menu abrir
        this.menu.connect('open-state-changed', (menu, open) => {
            if (open) {
                this._reloadActivities();
                this._render();
            }
        });
    }

    on_applet_clicked() {
        this.menu.toggle();
    }

    _updateClock() {
        let now = new Date();
        let hours = now.getHours();
        let minutes = now.getMinutes();
        let timeStr = (hours < 10 ? "0" + hours : hours) + ":" + (minutes < 10 ? "0" + minutes : minutes);
        this.set_applet_label(timeStr);
        return true; // Retornar true para continuar executando o timer
    }

    on_applet_removed_from_panel() {
        if (this._clockTimerId) {
            Mainloop.source_remove(this._clockTimerId);
            this._clockTimerId = null;
        }
    }

    _reloadActivities() {
        try {
            let filePath = GLib.get_home_dir() + "/.thebigcalendar/activities.json";
            let [success, content] = GLib.file_get_contents(filePath);
            if (success) {
                let jsonString = ByteArray.toString(content);
                this.activities = JSON.parse(jsonString);
            } else {
                this.activities = [];
            }
        } catch (e) {
            this.activities = [];
        }
    }

    _render() {
        this._renderLeftPane();
        this._renderRightPane();
    }

    _renderLeftPane() {
        this.leftPane.destroy_all_children();

        // Título com a Data Selecionada
        let year = this.selectedDate.getFullYear();
        let month = this.selectedDate.getMonth() + 1;
        let day = this.selectedDate.getDate();
        let dateStr = (day < 10 ? "0" + day : day) + "/" + (month < 10 ? "0" + month : month) + "/" + year;

        let titleLabel = new St.Label({
            text: "Eventos em " + dateStr,
            style: "font-weight: bold; font-size: 11pt; margin-bottom: 8px;"
        });
        this.leftPane.add_actor(titleLabel);

        // ScrollView para listar compromissos
        let scrollView = new St.ScrollView({
            hscrollbar_policy: St.PolicyType.NEVER,
            vscrollbar_policy: St.PolicyType.AUTOMATIC,
            style: "height: 200px;"
        });
        this.leftPane.add_actor(scrollView);

        let listContainer = new St.BoxLayout({ vertical: true, style: "spacing: 6px;" });
        scrollView.add_actor(listContainer);

        // Obter atividades do dia
        let dateString = year + "-" + (month < 10 ? "0" + month : month) + "-" + (day < 10 ? "0" + day : day);
        let dayActivities = this.activities.filter(act => act.date === dateString);

        if (dayActivities.length === 0) {
            let emptyLabel = new St.Label({
                text: "Nada agendado para este dia.",
                style: "font-style: italic; color: #888888; margin-top: 8px;"
            });
            listContainer.add_actor(emptyLabel);
        } else {
            dayActivities.forEach(act => {
                let card = new St.BoxLayout({
                    vertical: false,
                    style: "background-color: rgba(255, 255, 255, 0.05); padding: 8px; border-radius: 6px; spacing: 8px;"
                });
                
                // Barra de Prioridade
                let dotColor = this._getDotColorForActivity(act);
                let bar = new St.BoxLayout({
                    style: "width: 3px; height: 30px; border-radius: 1.5px; background-color: " + dotColor + ";"
                });
                if (dotColor === "#FFFFFF") {
                    bar.style += "border: 0.5px solid rgba(128, 128, 128, 0.5);";
                }
                card.add_actor(bar);

                let textContainer = new St.BoxLayout({ vertical: true });
                card.add_actor(textContainer);

                let title = new St.Label({
                    text: act.title,
                    style: "font-weight: bold; font-size: 9.5pt;"
                });
                if (act.isCompleted) {
                    title.style += "text-decoration: line-through; color: #888888;";
                }
                textContainer.add_actor(title);

                if (act.startTime) {
                    let time = new St.Label({
                        text: "Às " + act.startTime.slice(0, 5),
                        style: "font-size: 8pt; color: #888888;"
                    });
                    textContainer.add_actor(time);
                }

                listContainer.add_actor(card);
            });
        }

        // Botão para Abrir Aplicativo Desktop Completo
        let btnOpen = new St.Button({
            label: "Abrir Big Calendar",
            style_class: "calendar-open-button",
            style: "margin-top: 12px; padding: 6px 12px; border-radius: 6px; background-color: #3B82F6; text-align: center; color: white;"
        });
        btnOpen.connect('clicked', () => {
            this.menu.close();
            GLib.spawn_command_line_async("sh -c 'cd /home/marcos/kotlin_projects/TheBigCalendar && ./gradlew :desktop:run'");
        });
        this.leftPane.add_actor(btnOpen);
    }

    _renderRightPane() {
        this.rightPane.destroy_all_children();

        // Cabeçalho do Mês (Botões Prev, Nome Mês Ano, Next)
        let headerBox = new St.BoxLayout({ vertical: false, style: "spacing: 8px; margin-bottom: 8px;" });
        this.rightPane.add_actor(headerBox);

        let btnPrev = new St.Button({
            label: "‹",
            style: "width: 24px; font-weight: bold; font-size: 14pt; text-align: center;"
        });
        btnPrev.connect('clicked', () => {
            this.currentMonthDate.setMonth(this.currentMonthDate.getMonth() - 1);
            this._renderRightPane();
        });
        headerBox.add_actor(btnPrev);

        let monthsPT = [
            "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
            "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
        ];
        let monthName = monthsPT[this.currentMonthDate.getMonth()];
        let yearName = this.currentMonthDate.getFullYear();

        let monthLabel = new St.Label({
            text: monthName + " " + yearName,
            style: "font-weight: bold; font-size: 11pt; text-align: center; width: 180px; margin-top: 4px;"
        });
        headerBox.add_actor(monthLabel);

        let btnNext = new St.Button({
            label: "›",
            style: "width: 24px; font-weight: bold; font-size: 14pt; text-align: center;"
        });
        btnNext.connect('clicked', () => {
            this.currentMonthDate.setMonth(this.currentMonthDate.getMonth() + 1);
            this._renderRightPane();
        });
        headerBox.add_actor(btnNext);

        // Dias da Semana
        let weekdaysBox = new St.BoxLayout({ vertical: false, style: "margin-bottom: 6px;" });
        this.rightPane.add_actor(weekdaysBox);

        let weekdays = ["D", "S", "T", "Q", "Q", "S", "S"];
        weekdays.forEach(day => {
            let label = new St.Label({
                text: day,
                style: "width: 34px; text-align: center; font-weight: bold; font-size: 9pt; color: #888888;"
            });
            weekdaysBox.add_actor(label);
        });

        // Grade de Dias (6 semanas, 7 dias)
        let gridBox = new St.BoxLayout({ vertical: true, style: "spacing: 4px;" });
        this.rightPane.add_actor(gridBox);

        let year = this.currentMonthDate.getFullYear();
        let month = this.currentMonthDate.getMonth();
        let firstDay = new Date(year, month, 1);
        let lastDay = new Date(year, month + 1, 0);
        let totalDays = lastDay.getDate();
        let startOffset = firstDay.getDay(); // 0 = Dom, 6 = Sáb

        let today = new Date();

        let dayCounter = 1 - startOffset;
        for (let w = 0; w < 6; w++) {
            let row = new St.BoxLayout({ vertical: false, style: "spacing: 2px;" });
            gridBox.add_actor(row);

            for (let d = 0; d < 7; d++) {
                if (dayCounter > 0 && dayCounter <= totalDays) {
                    let dayNum = dayCounter;
                    
                    let btnStyle = "width: 32px; height: 36px; border-radius: 6px; padding: 2px;";
                    let isSelected = this.selectedDate.getDate() === dayNum &&
                                     this.selectedDate.getMonth() === month &&
                                     this.selectedDate.getFullYear() === year;
                    let isToday = today.getDate() === dayNum &&
                                  today.getMonth() === month &&
                                  today.getFullYear() === year;

                    if (isSelected) {
                        btnStyle += "background-color: #3B82F6; color: white;";
                    } else if (isToday) {
                        btnStyle += "background-color: rgba(59, 130, 246, 0.2); border: 1px solid #3B82F6;";
                    } else {
                        btnStyle += "background-color: rgba(255, 255, 255, 0.03);";
                    }

                    let dayButton = new St.Button({
                        style: btnStyle
                    });

                    let dayContent = new St.BoxLayout({ vertical: true, style: "spacing: 2px;" });
                    dayButton.set_child(dayContent);

                    // Número do dia
                    let numLabel = new St.Label({
                        text: dayNum.toString(),
                        style: "font-size: 9.5pt; text-align: center; font-weight: " + (isToday || isSelected ? "bold" : "normal") + ";"
                    });
                    dayContent.add_actor(numLabel);

                    // Fileira de Bolinhas (Dots) de Prioridade
                    let dotsRow = new St.BoxLayout({
                        vertical: false,
                        style: "spacing: 2px; margin-left: auto; margin-right: auto; height: 4px;"
                    });
                    dayContent.add_actor(dotsRow);

                    // Consultar atividades desse dia
                    let dateString = year + "-" + ((month + 1) < 10 ? "0" + (month + 1) : (month + 1)) + "-" + (dayNum < 10 ? "0" + dayNum : dayNum);
                    let dayActs = this.activities.filter(act => act.date === dateString);

                    // Renderizar até 3 bolinhas
                    dayActs.slice(0, 3).forEach(act => {
                        let dotColor = this._getDotColorForActivity(act);
                        let dot = new St.BoxLayout({
                            style: "width: 4px; height: 4px; border-radius: 2px; background-color: " + dotColor + ";"
                        });
                        dotsRow.add_actor(dot);
                    });

                    dayButton.connect('clicked', () => {
                        this.selectedDate = new Date(year, month, dayNum);
                        this._render();
                    });

                    row.add_actor(dayButton);
                } else {
                    let emptyBox = new St.BoxLayout({ style: "width: 32px; height: 36px;" });
                    row.add_actor(emptyBox);
                }
                dayCounter++;
            }
        }
    }

    _getDotColorForActivity(act) {
        if (act.activityType === "BIRTHDAY") return "#E91E63";
        if (act.activityType === "NOTE") return "#9C27B0";
        if (act.activityType === "COMMEMORATIVE") return "#FF9800";
        
        if (act.categoryColor && act.categoryColor.startsWith("#")) {
            return act.categoryColor;
        }
        
        switch (act.categoryColor) {
            case "1": return "#FFFFFF";
            case "2": return "#3B82F6";
            case "3": return "#FBBF24";
            case "4": return "#EF4444";
            default: return "#888888";
        }
    }
}

function main(metadata, orientation, panel_height, instance_id) {
    return new BigCalendarApplet(metadata, orientation, panel_height, instance_id);
}
