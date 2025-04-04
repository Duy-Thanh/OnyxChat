module.exports = (sequelize, DataTypes) => {
  const Message = sequelize.define('Message', {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true
    },
    senderId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'users',
        key: 'id'
      }
    },
    recipientId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'users',
        key: 'id'
      }
    },
    content: {
      type: DataTypes.TEXT,
      allowNull: false
    },
    contentType: {
      type: DataTypes.STRING,
      allowNull: false,
      defaultValue: 'text'
    },
    encrypted: {
      type: DataTypes.BOOLEAN,
      defaultValue: true
    },
    sent: {
      type: DataTypes.BOOLEAN,
      defaultValue: true
    },
    received: {
      type: DataTypes.BOOLEAN,
      defaultValue: false
    },
    receivedAt: {
      type: DataTypes.DATE,
      allowNull: true
    },
    read: {
      type: DataTypes.BOOLEAN,
      defaultValue: false
    },
    readAt: {
      type: DataTypes.DATE,
      allowNull: true
    },
    deleted: {
      type: DataTypes.BOOLEAN,
      defaultValue: false
    },
    deletedAt: {
      type: DataTypes.DATE,
      allowNull: true
    }
  }, {
    tableName: 'messages',
    timestamps: true
  });

  Message.associate = (models) => {
    // Message sender
    Message.belongsTo(models.User, {
      foreignKey: 'senderId',
      as: 'sender'
    });

    // Message recipient
    Message.belongsTo(models.User, {
      foreignKey: 'recipientId',
      as: 'recipient'
    });
  };

  return Message;
}; 